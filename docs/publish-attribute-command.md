# `sapl publish` — Attribut-Veröffentlichung via CLI

Der `publish`-Command ermöglicht es, Attribute direkt in das Attribut-Repository eines SAPL-Nodes zu schreiben — entweder über die REST-API eines laufenden Nodes oder direkt in eine Datenbank (Postgres / MongoDB), ohne dass ein Node laufen muss.

---

## Grundlegendes Konzept

Das SAPL Attribut-Repository speichert Schlüssel-Wert-Paare der Form:

```
(Entity, Attributname, Argumente) → Wert + TTL + Strategie
```

Der `publish`-Command befüllt dieses Repository. Dabei gibt es zwei Transportwege:

| Modus | Wann sinnvoll |
|---|---|
| `--url` | Ein SAPL-Node läuft bereits und ist erreichbar |
| `--provider` | Kein laufender Node, direkte DB-Verbindung gewünscht |

Genau einer der beiden Modi muss angegeben werden — beides gleichzeitig ist ein Fehler.

---

## Picocli: Bedingte Pflicht-Optionen mit `@ArgGroup`

Picocli kennt das Konzept von **Argument-Gruppen** (`@ArgGroup`). Damit lassen sich Optionen gruppieren und Abhängigkeiten zwischen ihnen ausdrücken.

### Exklusiv (`exclusive = true`)
Genau eine Option aus der Gruppe muss gesetzt sein. Mehrere gleichzeitig sind verboten.

### Ko-abhängig (`exclusive = false`)
Alle `required = true` Optionen innerhalb der Gruppe müssen gesetzt sein, sobald eine davon verwendet wird.

### Verschachtelte Gruppen
Durch Verschachtelung lassen sich komplexere Regeln ausdrücken. Im `publish`-Command sieht das so aus:

```
Äußere Gruppe (exclusive = true, multiplicity = "1"):
├── --url                          → API-Modus
└── Innere Gruppe (exclusive = false, multiplicity = "1"):
    ├── --provider  (required)     → z.B. "postgres" oder "mongo"
    ├── --db-url    (required)     → Verbindungs-URL
    ├── --db-username (optional)   → überschreibt URL-Credentials
    └── --db-password (optional)   → interaktiver Prompt wenn leer gelassen
```

**Resultat:** Entweder `--url` oder `--provider` + `--db-url` muss angegeben werden. Picocli erzwingt das automatisch und gibt eine verständliche Fehlermeldung aus, wenn es fehlt.

---

## Verwendung

### Modus 1: Über REST-API

```bash
sapl publish \
  --entity "user:alice" \
  --name "role" \
  --value "admin" \
  --url http://localhost:8443
```

Hier wird der Wert über HTTP/2 (h2c) an den laufenden SAPL-Node gesendet. Der Node nimmt den Wert entgegen und speichert ihn in seinem konfigurierten Storage.

### Modus 2: Direkt in Postgres

```bash
sapl publish \
  --entity "user:alice" \
  --name "role" \
  --value "admin" \
  --provider postgres \
  --db-url r2dbc:postgresql://localhost:5432/sapl \
  --db-username sapl \
  --db-password
```

Das Passwort wird interaktiv abgefragt (kein Echo), wenn `--db-password` ohne Wert angegeben wird. Alternativ können Credentials direkt in die URL eingebettet werden:

```bash
--db-url r2dbc:postgresql://sapl:secret@localhost:5432/sapl
```

### Modus 3: Direkt in MongoDB

```bash
sapl publish \
  --entity "user:alice" \
  --name "role" \
  --value "admin" \
  --provider mongo \
  --db-url mongodb://localhost:27017/sapl
```

Die Datenbank wird aus der URL extrahiert. Fehlt sie, wird `sapl` als Standard verwendet.

---

## Technische Umsetzung: Direkter Storage-Zugriff

Im Provider-Modus wird **kein Spring-Context gestartet**. Stattdessen werden die Verbindung und der Storage vollständig programmatisch aufgebaut:

```
CLI-Befehl
    │
    ├─── --url      → WebClient (HTTP/2 h2c) → REST-API des Nodes
    │
    └─── --provider → ConnectionFactory / MongoClient (programmatisch)
                           │
                           └── AttributeStorage.put(key, value).block()
```

### Warum kein Spring-Context?

Ein Spring-Boot-Application-Context bringt erheblichen Overhead mit sich (Bean-Scan, Auto-Configuration, Netty-Start etc.). Für einen einzelnen Schreibvorgang in der CLI sind das mehrere Sekunden unnötige Wartezeit.

Der direkte Ansatz ist möglich, weil `PostgresAttributeStorage` und `MongoAttributeStorage` einfache Klassen ohne Spring-Abhängigkeiten sind — sie brauchen nur einen `DatabaseClient` bzw. ein `ReactiveMongoTemplate`, die sich beide programmatisch erstellen lassen.

### Postgres-Verbindung (R2DBC)

```java
// URL parsen, optional Credentials überschreiben
var optionsBuilder = ConnectionFactoryOptions.parse(dbUrl).mutate();
if (dbUsername != null) optionsBuilder.option(USER, dbUsername);
if (dbPassword != null) optionsBuilder.option(PASSWORD, new String(dbPassword));

// ConnectionFactory → DatabaseClient → Storage
var storage = new PostgresAttributeStorage(
    DatabaseClient.create(ConnectionFactories.get(optionsBuilder.build())),
    new ObjectMapper()
);
```

R2DBC ist das reaktive Äquivalent zu JDBC. Die URL hat das Format `r2dbc:postgresql://host:port/database`.

### MongoDB-Verbindung

```java
var connectionString = new ConnectionString(mongoUrl);
var settingsBuilder = MongoClientSettings.builder().applyConnectionString(connectionString);

// Optionale Credential-Überschreibung
if (dbUsername != null && dbPassword != null) {
    settingsBuilder.credential(MongoCredential.createCredential(dbUsername, database, dbPassword));
}

var template = new ReactiveMongoTemplate(
    new SimpleReactiveMongoDatabaseFactory(MongoClients.create(settingsBuilder.build()), database)
);
var storage = new MongoAttributeStorage(template);
```

### Was wird gespeichert?

Beide Pfade landen in `publishToStorage()`, der einen `AttributeKey` und einen `PersistedAttribute`-Record erzeugt und direkt in den Storage schreibt:

```
AttributeKey       = (entity, attributeName, argumente=[])
PersistedAttribute = (wert, timestamp, ttl, strategie, deadline)
```

`deadline = Instant.now() + ttl` — nach diesem Zeitpunkt gilt das Attribut als abgelaufen. Die Strategie (`REMOVE` oder `BECOME_UNDEFINED`) bestimmt, was dann passiert.

---

## HTTP/2 im API-Modus: Warum WebClient?

Der SAPL-Node läuft mit `http2.enabled: true` und `ssl.enabled: false` — das ist **h2c (HTTP/2 Cleartext) im Prior-Knowledge-Modus**. Reactor Netty (der Server) erwartet dabei, dass Clients direkt mit HTTP/2-Frames beginnen.

Javas eingebauter `HttpClient` mit `HTTP_2` über Cleartext nutzt stattdessen den **HTTP Upgrade-Mechanismus** (`Upgrade: h2c` Header) — ein anderer Handshake, der vom Server nicht korrekt verarbeitet wird, besonders bei POST-Requests mit Body. Das Ergebnis war ein lautlos fehlender Request.

Die Lösung ist `WebClient` mit Reactor Netty und `HttpProtocol.H2C` auf Client-Seite:

```java
WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(
        reactor.netty.http.client.HttpClient.create().protocol(HttpProtocol.H2C)))
    .build();
```

`H2C` = Prior Knowledge auf Client-Seite → matcht exakt den Server-Modus. TLS wird nicht benötigt.

---

## Native Image (GraalVM)

Der Command funktioniert im nativen Binary weil:

- **Kein Reflection** — alle Objekte werden mit `new` erzeugt, kein Spring-DI
- **R2DBC-Treiber** (`r2dbc-postgresql`) bringt GraalVM Reachability Metadata mit und registriert seinen `ServiceLoader`-Eintrag automatisch
- **MongoDB-Treiber** (`mongodb-driver-reactivestreams`) hat seit Version 4.x native-image-Unterstützung
- **Picocli** hat explizite native-image-Unterstützung über `picocli-codegen`
- **WebClient / Reactor Netty** ist bereits im Build-Profil berücksichtigt

Der einzige Punkt, der manuell geprüft werden sollte: Jackson-Annotationen auf `PersistedAttribute` und `AttributeKey` müssen in der `reflect-config.json` registriert sein, damit die Serialisierung im nativen Binary funktioniert.

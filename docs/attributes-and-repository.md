# Attribute Repository & PIP — Vollständige Erklärung

## Überblick: Die Entscheidungskette

Wenn eine SAPL-Policy ein Attribut wie `subject.<user.role>` auswertet, durchläuft das System folgende Schritte:

```
Policy: subject.<user.plan>
            │
            ▼
   CachingAttributeBroker
   searchForMatchingPip()
            │
            ├─── Kein PIP für "user.plan" registriert?
            │         └──► Value.error("No unique policy information point found...")
            │
            ├─── PIP gefunden mit EXACT_MATCH (Argument-Anzahl passt exakt)?
            │         └──► PIP wird aufgerufen
            │                   └─── PIP gibt Flux.empty() zurück?
            │                             └──► Value.UNDEFINED
            │
            ├─── PIP gefunden mit VARARGS_MATCH (varargs, genug Argumente)?
            │         └──► PIP wird aufgerufen (wie oben)
            │
            └─── PIPs für den Namen registriert, aber keiner passt?
                      └──► AttributeRepository (Fallback!)
```

**Wichtig:** Der Repository-Fallback findet ausschließlich im Broker statt — auf Basis des Argument-Matchings. Es gibt keinen zweiten Fallback auf Stream-Ebene.

---

## Die drei Match-Typen (aus `AttributeFinderSpecification.matches()`)

| Match | Bedingung | Ergebnis |
|---|---|---|
| `EXACT_MATCH` | Argument-Anzahl == Anzahl fester Parameter | PIP wird bevorzugt verwendet |
| `VARARGS_MATCH` | PIP hat varargs (`Value... args`) UND Argument-Anzahl >= feste Parameter | PIP wird als Fallback verwendet, wenn kein EXACT_MATCH |
| `NO_MATCH` | Alles andere (falsche Anzahl, falscher Typ) | Wird übersprungen |

**Priorität:** EXACT_MATCH > VARARGS_MATCH > Repository > Error

---

## Was zählt als "Argument"?

Der **Entity**-Parameter (`subject`) zählt **nicht** als Argument. Arguments sind die zusätzlichen Werte in den Klammern der Policy-Syntax:

```sapl
subject.<user.plan("premium", "gold")>
         │          │           │
         │          └───────────┘
         Entity           └──► arguments = ["premium", "gold"]
         (= subject-Wert)
```

In der PIP-Methode:
```java
@Attribute(name = "plan")
public Flux<Value> plan(Value subject, Value arg1, Value arg2)
//                       ▲ entity      ▲ argument[0]  ▲ argument[1]
```

`subject` kommt aus der Policy, `arg1`/`arg2` kommen aus den Klammern. Beide stehen im `AttributeKey`:
```
AttributeKey = (entity=subject-Wert, attributeName="user.plan", arguments=["premium","gold"])
```

---

## Konkrete Analyse: `UserPolicyInformationPoint`

```java
@PolicyInformationPoint(name = "user")
public class UserPolicyInformationPoint {

    @Attribute(name = "age")
    public Flux<Value> age(Value subject, Value... args)    // varargs: 0+ args

    @Attribute(name = "department")
    public Flux<Value> department(Value subject, Value... args) // varargs: 0+ args

    @Attribute(name = "role")
    public Flux<Value> role(Value subject, Value... args)   // varargs: 0+ args

    @Attribute(name = "plan")
    public Flux<Value> plan(Value subject, Value arg1, Value arg2) // exakt 2 args
}
```

### Verhalten je Policy-Aufruf:

#### `user.age`, `user.department`, `user.role` (alle Varargs)

| Policy-Syntax | Arguments | Match | Ergebnis |
|---|---|---|---|
| `subject.<user.role>` | `[]` | VARARGS_MATCH (0 >= 0) | PIP aufgerufen → `Flux.empty()` → **UNDEFINED** |
| `subject.<user.role("x")>` | `["x"]` | VARARGS_MATCH (1 >= 0) | PIP aufgerufen → `Flux.empty()` → **UNDEFINED** |
| `subject.<user.role("x","y")>` | `["x","y"]` | VARARGS_MATCH | PIP aufgerufen → `Flux.empty()` → **UNDEFINED** |

> **Achtung:** Das Repository wird für `user.role` **niemals** verwendet, weil Varargs immer matchen. Der Kommentar `"PIP role called -> should NOT happen if repo fallback works"` im Code ist daher irreführend — das Repository kann hier strukturell nicht greifen.

#### `user.plan` (exakt 2 feste Arguments)

| Policy-Syntax | Arguments | Match | Ergebnis |
|---|---|---|---|
| `subject.<user.plan>` | `[]` | NO_MATCH (0 ≠ 2) | → **Repository-Fallback** |
| `subject.<user.plan("x")>` | `["x"]` | NO_MATCH (1 ≠ 2) | → **Repository-Fallback** |
| `subject.<user.plan("x","y")>` | `["x","y"]` | EXACT_MATCH (2 == 2) | PIP aufgerufen → `Flux.empty()` → **UNDEFINED** |
| `subject.<user.plan("x","y","z")>` | `["x","y","z"]` | NO_MATCH (3 ≠ 2) | → **Repository-Fallback** |

> **Erkenntnis:** Für `user.plan` ohne Arguments oder mit falscher Anzahl greift das Repository. Mit exakt 2 Arguments wird der PIP aufgerufen, der dann aber `Flux.empty()` zurückgibt — Ergebnis ist `UNDEFINED`, **nicht** ein Repository-Lookup.

---

## Was passiert wenn der PIP `Flux.empty()` zurückgibt?

In `AttributeStream.configureAttributeFinderStream()` steht:
```java
attributeFinder.invoke(invocation)
    .defaultIfEmpty(Value.UNDEFINED)  // ← kein sekundärer Repository-Lookup!
    .transform(this::addInitialTimeout)
    ...
```

`Flux.empty()` → `Value.UNDEFINED`. Das Repository wird dabei **nicht** nochmals befragt. Das ist ein bewusstes Design: PIP-Aufruf und Repository-Fallback schließen sich gegenseitig aus.

---

## Test-Policies

Alle Beispiele basieren auf dem `UserPolicyInformationPoint` wie er aktuell registriert ist.

### Policy 1: Simpler Repository-Lookup (kein Argument)
```sapl
policy "check user plan from repository"
permit
    subject.<user.plan> == "premium";
```
→ `user.plan` mit 0 args → NO_MATCH am PIP → **Repository-Fallback**  
→ Publish: `sapl publish --entity <subject-value> --name user.plan --value premium --url ...`

---

### Policy 2: Plan mit falschem Argument-Count → Repository
```sapl
policy "check plan with one arg — falls back to repo"
permit
    subject.<user.plan("tier-check")> == "gold";
```
→ 1 Argument, PIP erwartet 2 → NO_MATCH → **Repository-Fallback**  
→ Key im Repository: `(entity=subject, name="user.plan", arguments=["tier-check"])`  
→ Publish mit Argument nötig (sobald CLI das unterstützt)

---

### Policy 3: Varargs → PIP immer aufgerufen
```sapl
policy "role check — always hits PIP"
permit
    subject.<user.role> == "admin";
```
→ `role` hat Varargs → VARARGS_MATCH → PIP aufgerufen → gibt `Flux.empty()` zurück → `UNDEFINED`  
→ Diese Policy wird **niemals** `permit` ergeben, solange `role()` `Flux.empty()` zurückgibt.  
→ **Repository wird hier nicht verwendet!**

---

### Policy 4: Kombination aus mehreren Attributen
```sapl
policy "premium admin access"
permit
    action == "read"
    &
    subject.<user.plan> == "premium"
    &
    subject.<user.role> == "admin";
```

→ `user.plan` (0 args): Repository-Lookup  
→ `user.role` (0 args): PIP mit Varargs → `UNDEFINED` → Policy ergibt `INDETERMINATE`

Um diese Policy zum Funktionieren zu bringen, müsste `role` entweder:
- Im Repository gepublished werden UND die Varargs entfernt werden, ODER
- Der PIP müsste tatsächlich einen Wert aus einer Datenquelle liefern

---

### Policy 5: Korrekte Repository-only Policy (empfohlen für Evaluierung)
```sapl
policy "user role from repository only"
permit
    subject.<user.role> == "admin";
```
Damit das Repository für `user.role` greift, muss die PIP-Methode so angepasst werden:

```java
// Statt varargs: exakte Signatur ohne Argument
@Attribute(name = "role")
public Flux<Value> role(Value subject) {   // 0 feste Args, kein varargs
    return Flux.empty();                   // → Repository-Fallback bei 0 args in Policy
}
```
Dann: `subject.<user.role>` → 0 args, PIP erwartet 0 → EXACT_MATCH → PIP → `Flux.empty()` → **UNDEFINED**

Hmm, das ist auch nicht richtig für Repository. Die Lösung:

**Den PIP für `role` komplett nicht registrieren** (auskommentieren in `AttributeConfiguration`):
```java
// broker.loadPolicyInformationPointLibrary(new UserPolicyInformationPoint());
```
→ Dann: `subject.<user.role>` → kein PIP für "user.role" → **Error** (kein Fallback!)

**Oder:** Den PIP mit einer Signatur registrieren, die NICHT 0-args matcht:
```java
@Attribute(name = "role")
public Flux<Value> role(Value subject, Value requiredArg) { // exakt 1 arg
    return Flux.empty();
}
```
→ `subject.<user.role>` (0 args) → NO_MATCH → **Repository-Fallback** ✓  
→ `subject.<user.role("x")>` (1 arg) → EXACT_MATCH → PIP ✓

---

### Policy 6: Mehrere Attribute, alle aus Repository (sauberste Evaluierungs-Variante)
```sapl
policy "full user profile check"
permit
    action == "read"
    &
    resource == "documents"
    &
    subject.<user.plan> == "premium"
    &
    subject.<user.nickname> == "Bobster";
```

→ Beide Lookups (0 args) → NO_MATCH am PIP → Repository  
→ Im Repository publishen:

```bash
sapl publish --entity alice --name user.plan     --value premium --url http://localhost:8443
sapl publish --entity alice --name user.nickname --value Bobster --url http://localhost:8443
```

Authorization Request:
```json
{
  "subject": "alice",
  "action": "read",
  "resource": "documents"
}
```
→ Beide Attribute werden aus dem Repository gelesen → Policy ergibt `PERMIT`

---

## Zusammenfassung: Wann greift das Repository?

| Situation | Ergebnis |
|---|---|
| Kein PIP für den Attributnamen registriert | `Value.error(...)` — **kein** Repository |
| PIP hat Varargs → matcht immer | PIP wird aufgerufen — **kein** Repository |
| PIP hat exakt N feste Args, Policy übergibt N → EXACT_MATCH | PIP wird aufgerufen — **kein** Repository |
| PIP registriert, aber Argument-Anzahl passt zu keinem → NO_MATCH | **Repository-Fallback** ✓ |

**Faustregel:** Das Repository ist der "Catch-All" für den Fall, dass ein PIP zwar für den Namen registriert ist, aber die konkrete Aufruf-Signatur (Argument-Anzahl) nicht passt.

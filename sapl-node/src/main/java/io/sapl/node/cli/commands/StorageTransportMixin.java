package io.sapl.node.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.reactivestreams.client.MongoClients;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.nio.CharBuffer;
import java.util.Arrays;

import io.sapl.attributes.storage.AttributeStorage;
import io.sapl.attributes.storage.MongoAttributeStorage;
import io.sapl.attributes.storage.PostgresAttributeStorage;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.binding.BindMarkersFactory;
import picocli.CommandLine;

public class StorageTransportMixin {

    private static final ObjectMapper mapper;

    // Inner enum to distinct the different provider types for picocli
    private enum ProviderType {
        postgres,
        mongo
    }

    // Hint: empfohlen für Jackson ObjectMapper ODER ObjectReader / ObjectWriter aus
    // dem Mapper (ebenso thread-safe, immutable)
    // Designentscheidung ausstehend
    static {
        mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
    }

    // Hint: multiplicity = 1 → Entweder oder d.h. entweder --url oder --provider
    @CommandLine.ArgGroup(multiplicity = "1")
    Transport transport;

    // Hint: Picocli instanziert @ArgGroup Klassen intern per Reflection (Aufruf =
    // new Transport())
    // Nicht-statische Klasse müsste beim Aufruf ihre äußere Klasse kennen, aber
    // Picocli ruft die Klasse
    // nur über Class.forName(...<string>) auf, aber weiß noch nicht was genau
    // drinnen ist.
    static class Transport {
        @CommandLine.Option(names = "--url", description = "API endpoint of a running SAPL node")
        String url;

        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
        StorageProvider storageProvider;
    }

    static class StorageProvider {
        @CommandLine.Option(names = "--provider", required = true, description = "Supported storage provider types: ${COMPLETION-CANDIDATES}")
        ProviderType provider;

        @CommandLine.Option(names = "--db-url", required = true, description = "Database URL (e.g. r2dbc:postgresql://localhost:5432/sapl or mongodb://localhost:27017/sapl)")
        String dbUrl;

        @CommandLine.Option(names = "--db-username", description = "Database username — overrides credentials embedded in --db-url")
        String dbUsername;

        // Hint: char[] wird von Picocli nativ bei interactive = true unterstützt
        // Zusätzlich: char[] kann man direkt nach der Eingabe überschreiben und somit
        // ist das PW nicht mehr im Speicher
        // String hingegen bleibt im Heap bis der Garbage Collector es löscht
        // (Sekunden/Minuten)
        @CommandLine.Option(names = "--db-password", description = "Database password — omit to be prompted interactively", interactive = true, arity = "0..1")
        char[] dbPassword;
    }

    public AttributeStorage createStorage() {
        var provider = transport.storageProvider;

        return switch (provider.provider) {
        case postgres -> createPostgresStorage(provider);
        case mongo    -> createMongoStorage(provider);
        };
    }

    private AttributeStorage createPostgresStorage(StorageProvider provider) {
        var optionsBuilder = ConnectionFactoryOptions.parse(provider.dbUrl).mutate();
        if (provider.dbUsername != null) {
            optionsBuilder.option(ConnectionFactoryOptions.USER, provider.dbUsername);
        }

        // Hint: For security reasons. As soon as the password is sent to the host, it
        // will be emptied and not kept in memory
        if (provider.dbPassword != null) {
            try {
                optionsBuilder.option(ConnectionFactoryOptions.PASSWORD, CharBuffer.wrap(provider.dbPassword));
            } finally {
                Arrays.fill(provider.dbPassword, '\0');
            }
        }
        // BindMarkersFactory explizit setzen — DatabaseClient.create() würde
        // SpringFactoriesLoader
        // nutzen, was spring.factories aus allen JARs lädt und im native Image zu
        // Cascade-Fehlern führt.
        return new PostgresAttributeStorage(
                DatabaseClient.builder().connectionFactory(ConnectionFactories.get(optionsBuilder.build()))
                        .bindMarkers(BindMarkersFactory.indexed("$", 1)).build(),
                mapper);
    }

    private AttributeStorage createMongoStorage(StorageProvider provider) {
        var connectionString = new ConnectionString(provider.dbUrl);
        var database         = connectionString.getDatabase() != null ? connectionString.getDatabase() : "sapl";
        var settingsBuilder  = MongoClientSettings.builder().applyConnectionString(connectionString);

        // Hint: For security reasons. As soon as the password is sent to the host, it
        // will be emptied and not kept in memory
        if (provider.dbUsername != null && provider.dbPassword != null) {
            try {
                settingsBuilder.credential(
                        MongoCredential.createCredential(provider.dbUsername, database, provider.dbPassword));
            } finally {
                Arrays.fill(provider.dbPassword, '\0');
            }
        }
        var mongoClient = MongoClients.create(settingsBuilder.build());
        var template    = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(mongoClient, database));
        return new MongoAttributeStorage(template, mapper);
    }
}

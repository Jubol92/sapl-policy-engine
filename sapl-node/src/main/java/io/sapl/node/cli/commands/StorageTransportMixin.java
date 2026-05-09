package io.sapl.node.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.reactivestreams.client.MongoClients;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.attributes.storage.MongoAttributeStorage;
import io.sapl.attributes.storage.PostgresAttributeStorage;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import picocli.CommandLine;

public class StorageTransportMixin {

    private static final ObjectMapper mapper;

    // Inner enum to distinct the different provider types for picocli
    private enum ProviderType {
        postgres,
        mongo
    }

    static {
        mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
    }

    @CommandLine.ArgGroup(multiplicity = "1")
    Transport transport;

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

        @CommandLine.Option(names = "--db-password", description = "Database password — omit to be prompted interactively", interactive = true, arity = "0..1")
        char[] dbPassword;
    }

    public AttributeStorage createStorage() {
        var sp = transport.storageProvider;

        return switch (sp.provider) {
        case postgres -> createPostgresStorage(sp);
        case mongo    -> createMongoStorage(sp);
        };
    }

    private AttributeStorage createPostgresStorage(StorageProvider provider) {
        var optionsBuilder = ConnectionFactoryOptions.parse(provider.dbUrl).mutate();
        if (provider.dbUsername != null) {
            optionsBuilder.option(ConnectionFactoryOptions.USER, provider.dbUsername);
        }
        if (provider.dbPassword != null) {
            optionsBuilder.option(ConnectionFactoryOptions.PASSWORD, new String(provider.dbPassword));
        }
        return new PostgresAttributeStorage(DatabaseClient.create(ConnectionFactories.get(optionsBuilder.build())),
                mapper);
    }

    private AttributeStorage createMongoStorage(StorageProvider sp) {
        var connectionString = new ConnectionString(sp.dbUrl);
        var database         = connectionString.getDatabase() != null ? connectionString.getDatabase() : "sapl";
        var settingsBuilder  = MongoClientSettings.builder().applyConnectionString(connectionString);
        if (sp.dbUsername != null && sp.dbPassword != null) {
            settingsBuilder.credential(MongoCredential.createCredential(sp.dbUsername, database, sp.dbPassword));
        }
        var mongoClient = MongoClients.create(settingsBuilder.build());
        var template    = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(mongoClient, database));
        return new MongoAttributeStorage(template);
    }
}

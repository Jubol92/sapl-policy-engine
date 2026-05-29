package io.sapl.node.attributes;

import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AttributeService {
    private final AttributeRepository                     repository;
    private final ConcurrentHashMap<RepositoryKey, Value> snapshot = new ConcurrentHashMap<>();
    private final ObjectMapper                            mapper;     // needed for JsonNode

    public void publish(String entity, String attribute, long ttl, PushRequest body) {
        List<Value> arguments = body.getArguments() == null ? List.of()
                : body.getArguments().stream().map(this::fromJson).toList();

        Value         entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;
        Value         value       = fromJson(body.getValue());
        RepositoryKey key         = new RepositoryKey(entityValue, attribute, arguments);

        if (ttl <= 0) {
            repository.publish(key, value);
        } else {
            repository.publish(key, value, Duration.ofSeconds(ttl));
        }
        snapshot.put(key, value);
    }

    public void delete(String entity, String attribute, List<String> rawArgs) {
        List<Value> arguments = rawArgs == null ? List.of() : rawArgs.stream().map(this::fromString).toList();

        Value         entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;
        RepositoryKey key         = new RepositoryKey(entityValue, attribute, arguments);

        repository.remove(key);
        snapshot.remove(key);
    }

    public String get(String entity, String attribute, List<String> rawArgs) {
        List<Value>   arguments   = rawArgs == null ? List.of() : rawArgs.stream().map(this::fromString).toList();
        Value         entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;
        RepositoryKey key         = new RepositoryKey(entityValue, attribute, arguments);
        if (!snapshot.containsKey(key))
            throw new NoSuchElementException();
        return snapshot.get(key).toString();
    }

    public String getAll(String entity) {
        Value entityValue = Value.of(entity);
        return snapshot.entrySet().stream().filter(e -> Objects.equals(e.getKey().entity(), entityValue))
                .map(e -> e.getKey().name() + ": " + e.getValue()).reduce((a, b) -> a + "\n" + b)
                .orElse("No attributes found");
    }

    // Converts the type within the JsonNode to the SAPL value
    // Needed if the data is sent in the body as JSON data
    private Value fromJson(JsonNode data) {
        if (data == null || data.isNull())
            return Value.NULL;
        if (data.isBoolean())
            return Value.of(data.booleanValue());
        if (data.isIntegralNumber())
            return Value.of(data.longValue());
        if (data.isNumber())
            return Value.of(data.doubleValue());
        return Value.of(data.stringValue());
    }

    // Converts a given string value into a SAPL value
    // Necessary if request is sent via URL parameter like <URL>/<API>?arg1=dsd
    private Value fromString(String data) {
        try {
            return fromJson(mapper.readTree(data));
        } catch (Exception e) {
            return Value.of(data);
        }
    }
}

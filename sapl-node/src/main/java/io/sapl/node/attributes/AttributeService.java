package io.sapl.node.attributes;

import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AttributeService {
    private final AttributeRepository                     repository;
    private final ConcurrentHashMap<RepositoryKey, Value> snapshot = new ConcurrentHashMap<>();

    public String publish(String entity, String attribute, long ttl, PushRequest body) {
        List<Value> arguments = body.getArguments() == null ? List.of()
                : body.getArguments().stream().map(this::convertValue).filter(Objects::nonNull).toList();

        Value         entityValue = entity != null && !entity.isBlank() ? convertStringArg(entity) : null;
        Value         value       = convertStringArg(body.getValue());
        RepositoryKey key         = new RepositoryKey(entityValue, attribute, arguments);

        if (ttl <= 0) {
            repository.publish(key, value);
        } else {
            repository.publish(key, value, Duration.ofSeconds(ttl));
        }
        snapshot.put(key, value);

        return "Attribute published";
    }

    public String delete(String entity, String attribute, List<String> rawArgs) {
        List<Value> arguments = rawArgs == null ? List.of() : rawArgs.stream().map(this::convertStringArg).toList();

        Value         entityValue = entity != null && !entity.isBlank() ? convertStringArg(entity) : null;
        RepositoryKey key         = new RepositoryKey(entityValue, attribute, arguments);

        repository.remove(key);
        snapshot.remove(key);

        return "Attribute deleted";
    }

    public String get(String entity, String attribute, List<String> rawArgs) {
        List<Value>   arguments   = rawArgs == null ? List.of() : rawArgs.stream().map(this::convertStringArg).toList();
        Value         entityValue = entity != null && !entity.isBlank() ? convertStringArg(entity) : null;
        RepositoryKey key         = new RepositoryKey(entityValue, attribute, arguments);
        Value         value       = snapshot.get(key);
        return value != null ? value.toString() : "Not found";
    }

    public String getAll(String entity) {
        Value entityValue = convertStringArg(entity);
        return snapshot.entrySet().stream().filter(e -> Objects.equals(e.getKey().entity(), entityValue))
                .map(e -> e.getKey().name() + ": " + e.getValue()).reduce((a, b) -> a + "\n" + b)
                .orElse("No attributes found");
    }

    private Value convertValue(Object input) {
        return switch (input) {
        case null      -> Value.NULL;
        case Boolean b -> Value.of(b);
        case Integer i -> Value.of(i.longValue());
        case Long l    -> Value.of(l);
        case Double v  -> Value.of(v);
        case String s  -> Value.of(s);
        default        -> null;
        };
    }

    private Value convertStringArg(String input) {
        if (input == null)
            return Value.NULL;
        if (input.equalsIgnoreCase("true"))
            return Value.of(true);
        if (input.equalsIgnoreCase("false"))
            return Value.of(false);
        try {
            return Value.of(Long.parseLong(input));
        } catch (NumberFormatException ignored) {
        }
        try {
            return Value.of(Double.parseDouble(input));
        } catch (NumberFormatException ignored) {
        }
        return Value.of(input);
    }
}

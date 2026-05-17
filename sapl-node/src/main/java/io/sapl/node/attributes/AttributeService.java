package io.sapl.node.attributes;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.attributes.AttributeRepository.TimeOutStrategy;
import io.sapl.api.attributes.PersistedAttribute;
import io.sapl.api.model.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AttributeService {
    private final AttributeRepository repository;

    public Mono<String> publish(String entity, String attribute, Long ttl, String strategy, PushRequest body) {
        boolean hasEntity = entity != null && !entity.isBlank();

        // Fallback to default strategy
        if (!strategy.equalsIgnoreCase("remove") && !strategy.equalsIgnoreCase("become_undefined")) {
            strategy = "REMOVE";
        }
        TimeOutStrategy timeout = TimeOutStrategy.valueOf(strategy.toUpperCase());

        // Fallback to infinite TTL
        Duration duration = (ttl <= 0 || ttl == Long.MAX_VALUE) ? AttributeRepository.INFINITE
                : Duration.ofSeconds(ttl);

        // Arguments are allowed to be empty, but otherwise they need to be parsed.
        // Filter only non-null values
        // @formatter:off
        List<Value> arguments = body.getArguments() == null ? List.of() : body.getArguments().stream()
                .map(this::convertValue)
                .filter(Objects::nonNull)
                .toList();
        // @formatter:on

        Value value = convertStringArg(body.getValue());

        // @formatter:off
        if (hasEntity) {
            return repository.publishAttribute(convertStringArg(entity), attribute, arguments, value, duration, timeout)
                    .thenReturn("Attribute published");
        } else {
            return repository.publishAttribute(attribute, arguments, value, duration, timeout)
                    .thenReturn("Attribute published");
        }
        // @formatter:on
    }

    public Mono<Void> delete(String entity, String attribute, List<String> rawArgs) {
        boolean hasEntity = entity != null && !entity.isBlank();

        // Arguments are allowed to be empty, but otherwise they need to be parsed.
        // Filter only non-null values
        // @formatter:off
        List<Value> arguments = rawArgs == null ? List.of() : rawArgs.stream()
                                                              .map(this::convertStringArg)
                                                              .toList();
        // @formatter:on

        // Case 1: without entity aka global attribute
        // Case 2: with entity and attribute name
        // Case 3: with entity, attribute name and args as request params
        // Case 4: without entity but attribute name and args as request params
        // else: fail

        if (hasEntity) {
            return repository.removeAttribute(convertStringArg(entity), attribute, arguments);
        } else {
            return repository.removeAttribute(attribute, arguments);
        }
    }

    public Mono<PersistedAttribute> get(String entity, String attribute, List<String> rawArgs) {
        boolean hasEntity = entity != null && !entity.isBlank();

        List<Value> arguments = rawArgs == null ? List.of() : rawArgs.stream().map(this::convertStringArg).toList();

        Value        entityValue = hasEntity ? convertStringArg(entity) : null;
        AttributeKey key         = new AttributeKey(entityValue, attribute, arguments);

        return repository.getAllAttributes().filter(entry -> entry.getKey().equals(key)).next()
                .map(Map.Entry::getValue);
    }

    public Flux<PersistedAttribute> getAll(String entity) {
        return repository.getAttributeForEntity(convertStringArg(entity));
    }

    // Converts the input into a proper SAPL value
    private Value convertValue(Object input) {
        switch (input) {
        case null      -> {
            return Value.NULL;
        }
        case Boolean b -> {
            return Value.of(b);
        }
        case Integer i -> {
            return Value.of(i.longValue());
        }
        case Long l    -> {
            return Value.of(l);
        }
        case Double v  -> {
            return Value.of(v);
        }
        case String s  -> {
            return Value.of(s);
        }
        default        -> {
            return null;
        }
        }
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

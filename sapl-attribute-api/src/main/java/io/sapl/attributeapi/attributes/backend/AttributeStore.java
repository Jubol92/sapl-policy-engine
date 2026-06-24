package io.sapl.attributeapi.backend;

import io.sapl.api.model.Value;

import java.time.Duration;
import java.util.List;

public interface AttributeStore {
    void publish(Value entity, String name, List<Value> arguments, Value value);

    void publish(Value entity, String name, List<Value> arguments, Value value, Duration ttl);

    void remove(Value entity, String name, List<Value> arguments);

    Value get(Value entity, String name, List<Value> arguments);
}

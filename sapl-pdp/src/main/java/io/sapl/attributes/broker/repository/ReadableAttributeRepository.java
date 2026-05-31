package io.sapl.attributes.broker.repository;

import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;

public interface ReadableAttributeRepository extends AttributeRepository {
    Value get(RepositoryKey key);
}

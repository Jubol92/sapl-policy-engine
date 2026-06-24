package io.sapl.attributeapi.attributes.auth;

import org.springframework.security.core.Authentication;

@FunctionalInterface
@SuppressWarnings("unused")
public interface PdpIdExtractor {
    String authContext(Authentication authentication);
}
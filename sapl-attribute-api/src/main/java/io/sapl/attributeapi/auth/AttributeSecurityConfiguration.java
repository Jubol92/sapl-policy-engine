/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.attributeapi.auth;

import io.sapl.attributeapi.auth.ApiKey.ApiKeyAuthenticationService;
import io.sapl.attributeapi.auth.ApiKey.ApiKeyReactiveAuthenticationManager;
import io.sapl.attributeapi.auth.ApiKey.ApiKeyServerAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(AttributeApiSecurityProperties.class)
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class AttributeSecurityConfiguration {
    private final AttributeApiSecurityProperties properties;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // CSRF is not needed because we have a stateless API
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        if (noAuthenticationMechanismIsDefined()) {
            throw new IllegalStateException("No authentication method set");
        }

        if (properties.isAllowNoAuth() && !properties.isAllowBasicAuth()) {
            log.warn("Server has been configured to reply to requests without authentication.");
            return http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                    .authorizeExchange(exchange -> exchange.anyExchange().permitAll()).build();
        }

        if (properties.isAllowBasicAuth()) {
            log.info("Basic authentication activated.");
            if (!hasBasicAuthUsers()) {
                log.warn("Basic authentication is enabled but no users with basic credentials are configured.");
            }
            http.httpBasic(withDefaults());
        } else {
            http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        }

        if (properties.isAllowApiKeyAuth()) {
            log.info("API key authentication activated.");

            if (!hasApiKeyUsers()) {
                log.warn("API key authentication is enabled but no api key is defined.");
            }

            AuthenticationWebFilter apiKeyFilter = new AuthenticationWebFilter(
                    new ApiKeyReactiveAuthenticationManager(new ApiKeyAuthenticationService(properties)));
            apiKeyFilter.setServerAuthenticationConverter(new ApiKeyServerAuthenticationConverter());
            http.addFilterAfter(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        }

        http.authorizeExchange(exchange -> exchange.anyExchange().authenticated());

        return http.build();
    }

    @Bean
    public ReactiveUserDetailsService reactiveUserDetailsService() {
        return new AttributeApiUserDetailsService(properties);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    private boolean noAuthenticationMechanismIsDefined() {
        return !properties.isAllowNoAuth() && !properties.isAllowBasicAuth() && !properties.isAllowApiKeyAuth();
    }

    private boolean hasBasicAuthUsers() {
        return properties.getUsers().stream().anyMatch(user -> user.getBasic() != null);
    }

    private boolean hasApiKeyUsers() {
        return properties.getUsers().stream().anyMatch(user -> user.getKey() != null);
    }
}

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
package io.sapl.attributeapi.auth.ApiKey;

import io.sapl.attributeapi.auth.AttributeApiUserDetails;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;

public class ApiKeyReactiveAuthenticationManager implements ReactiveAuthenticationManager {
    private final ApiKeyAuthenticationService service;

    public ApiKeyReactiveAuthenticationManager(ApiKeyAuthenticationService service) {
        this.service = service;
    }

    @Override
    @NullMarked
    public Mono<Authentication> authenticate(Authentication authentication) {
        String key = Objects.requireNonNull(authentication.getCredentials()).toString();
        try {
            Optional<AttributeApiUserDetails> user = service.findByApiKey(key);

            if (user.isPresent()) {
                AttributeApiUserDetails details = user.get();
                return Mono.just(
                        UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
            } else {
                return Mono.error((new BadCredentialsException("Invalid API key")));
            }

        } catch (NoSuchAlgorithmException e) {
            return Mono.error(new IllegalStateException(e));
        }
    }
}

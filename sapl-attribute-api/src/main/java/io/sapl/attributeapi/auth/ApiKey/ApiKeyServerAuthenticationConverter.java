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

import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ApiKeyServerAuthenticationConverter implements ServerAuthenticationConverter {
    @Override
    @NullMarked
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        HttpHeaders header     = exchange.getRequest().getHeaders();
        String      authHeader = header.getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer sapl_")) {
            String key = authHeader.substring(12);
            return Mono.just(UsernamePasswordAuthenticationToken.unauthenticated(key, key));
        }
        return Mono.empty();
    }
}

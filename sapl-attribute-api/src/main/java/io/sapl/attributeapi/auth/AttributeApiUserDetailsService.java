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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class AttributeApiUserDetailsService implements ReactiveUserDetailsService {

    private final AttributeApiSecurityProperties properties;

    @Override
    @NullMarked
    public Mono<UserDetails> findByUsername(String username) {
        return Mono.justOrEmpty(properties.getUsers().stream()
                .filter(user -> user.getBasic() != null && username.equals(user.getBasic().getUsername())).findFirst()
                .map(user -> new AttributeApiUserDetails(user.getBasic().getUsername(), user.getBasic().getSecret(),
                        user.getTenantId())));
    }
}

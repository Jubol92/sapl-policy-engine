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
package io.sapl.attributeapi.attributes.controller;

import io.sapl.attributeapi.attributes.dto.AttributePublishRequest;
import io.sapl.attributeapi.attributes.service.AttributeApiService;
import io.sapl.attributeapi.auth.AttributeApiUserDetails;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/attributes")
@SuppressWarnings("unused")
public class AttributeApiController {
    private static final String NO_TENANT_ID = "";

    private final AttributeApiService service;

    @PostMapping("/{entity}/{name}")
    public Mono<ResponseEntity<Void>> publish(@PathVariable String entity, @PathVariable String name,
            @RequestBody AttributePublishRequest request) {
        return currentTenantId()
                .flatMap(tenantId -> Mono.fromRunnable(() -> service.publish(entity, name, request, tenantId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .thenReturn(ResponseEntity.created(URI.create("/api/attributes/" + entity + "/" + name)).build());
    }

    @PostMapping("/{name}")
    public Mono<ResponseEntity<Void>> publishGlobalAttribute(@PathVariable String name,
            @RequestBody AttributePublishRequest request) {
        return currentTenantId()
                .flatMap(tenantId -> Mono.fromRunnable(() -> service.publish(null, name, request, tenantId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .thenReturn(ResponseEntity.created(URI.create("/api/attributes/" + name)).build());
    }

    // RFC 7231, Section 4.3.5: A payload within a DELETE request message has no
    // defined semantics;
    // sending a payload body on a DELETE request might cause some existing
    // implementations to reject the request
    // Some clients may ignore in Delete-Request the body, so it's an URL parameter
    @DeleteMapping("/{entity}/{name}")
    public Mono<ResponseEntity<Void>> deleteAttribute(@PathVariable String entity, @PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return currentTenantId()
                .flatMap(tenantId -> Mono.fromRunnable(() -> service.delete(entity, name, args, tenantId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .thenReturn(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{name}")
    public Mono<ResponseEntity<Void>> deleteGlobalAttribute(@PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return currentTenantId().flatMap(tenantId -> Mono.fromRunnable(() -> service.delete(null, name, args, tenantId))
                .subscribeOn(Schedulers.boundedElastic())).thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/{entity}/{name}")
    public Mono<ResponseEntity<JsonNode>> getAttribute(@PathVariable String entity, @PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return currentTenantId().flatMap(tenantId -> Mono.fromCallable(() -> service.get(entity, name, args, tenantId))
                .subscribeOn(Schedulers.boundedElastic())).map(ResponseEntity::ok);
    }

    @GetMapping("/{name}")
    public Mono<ResponseEntity<JsonNode>> getGlobalAttribute(@PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return currentTenantId().flatMap(tenantId -> Mono.fromCallable(() -> service.get(null, name, args, tenantId))
                .subscribeOn(Schedulers.boundedElastic())).map(ResponseEntity::ok);
    }

    @GetMapping
    public Mono<ResponseEntity<List<JsonNode>>> getAllAttributesFromTenant(
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {

        return currentTenantId().flatMap(tenantId -> Mono.fromCallable(() -> service.getAll(tenantId, limit, offset))
                .subscribeOn(Schedulers.boundedElastic())).map(ResponseEntity::ok);
    }

    @GetMapping("/_count")
    public Mono<ResponseEntity<Long>> count() {
        return currentTenantId().flatMap(
                tenantId -> Mono.fromCallable(() -> service.count(tenantId)).subscribeOn(Schedulers.boundedElastic()))
                .map(ResponseEntity::ok);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidArgument(IllegalArgumentException e) {
        log.warn(e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }

    // Resolves the tenantId of the authenticated principal. Falls back to
    // NO_TENANT_ID (which AttributeApiService treats the same as null) when
    // no AttributeApiUserDetails is present, e.g. in no-auth mode.
    private Mono<String> currentTenantId() {
        return ReactiveSecurityContextHolder.getContext().mapNotNull(SecurityContext::getAuthentication)
                .mapNotNull(Authentication::getPrincipal).filter(AttributeApiUserDetails.class::isInstance)
                .cast(AttributeApiUserDetails.class).mapNotNull(AttributeApiUserDetails::getTenantId)
                .defaultIfEmpty(NO_TENANT_ID);
    }
}

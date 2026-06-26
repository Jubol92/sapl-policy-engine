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
import io.sapl.attributeapi.attributes.dto.AttributeValueResponse;
import io.sapl.attributeapi.attributes.service.AttributeApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
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
    private final AttributeApiService service;

    @PostMapping("/{entity}/{name}")
    public Mono<ResponseEntity<Void>> publish(@PathVariable String entity, @PathVariable String name,
            @RequestBody AttributePublishRequest request) {
        return Mono.fromRunnable(() -> service.publish(entity, name, request)).subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.created(URI.create("/api/attributes/" + entity + "/" + name)).build());
    }

    @PostMapping("/{name}")
    public Mono<ResponseEntity<Void>> publishGlobalAttribute(@PathVariable String name,
            @RequestBody AttributePublishRequest request) {
        return Mono.fromRunnable(() -> service.publish(null, name, request)).subscribeOn(Schedulers.boundedElastic())
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
        return Mono.fromRunnable(() -> service.delete(entity, name, args)).subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{name}")
    public Mono<ResponseEntity<Void>> deleteGlobalAttribute(@PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return Mono.fromRunnable(() -> service.delete(null, name, args)).subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/{entity}/{name}")
    public Mono<ResponseEntity<AttributeValueResponse>> getAttribute(@PathVariable String entity,
            @PathVariable String name, @RequestParam(value = "arg", required = false) List<String> args) {
        return Mono.fromCallable(() -> service.get(entity, name, args)).subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{name}")
    public Mono<ResponseEntity<AttributeValueResponse>> getGlobalAttribute(@PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return Mono.fromCallable(() -> service.get(null, name, args)).subscribeOn(Schedulers.boundedElastic())
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
}

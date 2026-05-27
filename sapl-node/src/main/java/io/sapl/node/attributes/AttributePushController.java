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
package io.sapl.node.attributes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@SuppressWarnings("unused")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/attributes")
public class AttributePushController {
    private final AttributeService service;

    @SuppressWarnings("unused")
    @PostMapping("/{entity}/{name}")
    public ResponseEntity<Void> publish(@PathVariable String entity, @PathVariable String name,
            @RequestHeader(value = "ttl", defaultValue = "-1") long ttl, @RequestBody PushRequest request) {
        service.publish(entity, name, ttl, request);
        return ResponseEntity.created(URI.create("/api/attributes/" + entity + "/" + name)).build();
    }

    @SuppressWarnings("unused")
    @PostMapping("/{name}")
    public ResponseEntity<Void> publishGlobalAttribute(@PathVariable String name,
            @RequestHeader(value = "ttl", defaultValue = "-1") long ttl, @RequestBody PushRequest request) {
        service.publish(null, name, ttl, request);
        return ResponseEntity.created(URI.create("/api/attributes/" + name)).build();
    }

    // RFC 7231, Section 4.3.5: A payload within a DELETE request message has no
    // defined semantics;
    // sending a payload body on a DELETE request might cause some existing
    // implementations to reject the request
    // Some clients may ignore in Delete-Request the body, so it's an URL parameter
    @SuppressWarnings("unused")
    @DeleteMapping("/{entity}/{name}")
    public ResponseEntity<Void> deleteAttribute(@PathVariable String entity, @PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        service.delete(entity, name, args);
        return ResponseEntity.noContent().build();
    }

    // RFC 7231, Section 4.3.5: A payload within a DELETE request message has no
    // defined semantics;
    // sending a payload body on a DELETE request might cause some existing
    // implementations to reject the request
    // Some clients may ignore in Delete-Request the body, so it's an URL parameter1
    @SuppressWarnings("unused")
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteGlobalAttribute(@PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {

        service.delete(null, name, args);
        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unused")
    @GetMapping("/{entity}/{name}")
    public ResponseEntity<String> getAttribute(@PathVariable String entity, @PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return ResponseEntity.ok(service.get(entity, name, args));
    }

    @SuppressWarnings("unused")
    @GetMapping("/{name}")
    public ResponseEntity<String> getGlobalAttribute(@PathVariable String name,
            @RequestParam(value = "arg", required = false) List<String> args) {
        return ResponseEntity.ok(service.get(null, name, args));
    }

    // Spring prefers literal path segments over variables, so /entity/{entity}
    // takes priority over /{name}
    @SuppressWarnings("unused")
    @GetMapping("/entity/{entity}")
    public ResponseEntity<String> getAllAttributesOfEntity(@PathVariable String entity) {
        return ResponseEntity.ok(service.getAll(entity));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleInvalidArgument(IllegalArgumentException e) {
        log.warn(e.getMessage());
        // ggf. RFC 7808 konform? JSON mit error message
        return ResponseEntity.badRequest().build();
    }
}

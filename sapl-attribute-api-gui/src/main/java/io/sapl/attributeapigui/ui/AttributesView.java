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
package io.sapl.attributeapigui.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.sapl.attributeapigui.client.AttributeApiClient;
import jakarta.annotation.security.RolesAllowed;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Route(value = "", layout = MainLayout.class)
@PageTitle("Attributes")
@RolesAllowed("ADMIN")
public class AttributesView extends VerticalLayout {
    // Internal http client
    private final AttributeApiClient client;

    // Search fields
    private final TextField entityField = new TextField();
    private final TextField keyField    = new TextField();

    // Grid to display the data
    private final Grid<Map<String, Object>> grid = new Grid<>();

    public AttributesView(AttributeApiClient client) {
        this.client = client;

        // Basic settings
        setSizeFull();
        add(new H2("Repository overview"));

        grid.addColumn(entry -> String.valueOf(entry.get("entity"))).setHeader("Entity").setAutoWidth(true);
        grid.addColumn(entry -> String.valueOf(entry.get("name"))).setHeader("Name").setAutoWidth(true);
        grid.addColumn(entry -> String.valueOf(entry.get("arguments"))).setHeader("Arguments").setAutoWidth(true);
        grid.addColumn(entry -> String.valueOf(entry.get("value"))).setHeader("Value").setAutoWidth(true);
        grid.setSizeFull();
        grid.setItems(List.of());

        // Key events for the grid - Grid does not implement KeyNotifier, so a
        // Shortcut scoped to the grid is used instead of addKeyDownListener.
        Shortcuts.addShortcutListener(grid, () -> {
            var selected = grid.asSingleSelect().getValue();
            if (selected == null) {
                return;
            }
            var entity = selected.get("entity");
            deleteItem(entity == null ? null : entity.toString(), selected.get("name").toString());
        }, Key.DELETE).listenOn(grid);

        // Search fields
        entityField.setPlaceholder("optional");
        entityField.setPrefixComponent(new Span("entity ="));
        entityField.addKeyPressListener(Key.ENTER, event -> search());

        keyField.setPlaceholder("key");
        keyField.setPrefixComponent(new Span("key ="));
        keyField.addKeyPressListener(Key.ENTER, event -> search());

        var searchButton = new Button("Search", event -> search());
        var searchRow    = new HorizontalLayout(entityField, keyField, searchButton);
        add(searchRow, grid);
        setFlexGrow(1, grid);
        // End search fields
    }

    private void search() {
        var key    = keyField.getValue();
        var entity = entityField.getValue();

        try {
            if (key == null || key.isBlank()) {
                var entries = client.getAllAttributes();
                grid.setItems(entries);
                if (entries.isEmpty()) {
                    Notification.show("No attributes found.");
                }
            } else {
                var value = client.getAttribute(entity, key.trim());
                if (value.isPresent()) {
                    grid.setItems(List.of(
                            Map.of("entity", entity == null ? "" : entity, "name", key.trim(), "value", value.get())));
                } else {
                    grid.setItems(List.of());
                    Notification.show("No attribute found for key '" + key.trim() + "'.");
                }
            }
        } catch (RuntimeException e) {
            log.warn("Failed to look up attributes (key '{}', entity '{}')", key, entity, e);
            var notification = Notification.show("Search failed: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteItem(String entity, String name) {
        if (client.deleteAttribute(entity, name)) {
            search();
        }
    }
}

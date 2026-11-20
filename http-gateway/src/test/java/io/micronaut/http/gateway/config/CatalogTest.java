/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.gateway.config;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table of the guide that lists the predicates and filters of the configuration, and their
 * shortcut arguments, is the catalog of the built-in factories: a factory added without a row,
 * or a row without a factory, fails this test.
 */
class CatalogTest {

    private static final Pattern ROW = Pattern.compile("^\\|`([A-Za-z]+)` \\|(predicate|filter) \\|([^|]*)\\|", Pattern.MULTILINE);

    @Test
    void theGuideListsEveryBuiltInPredicateAndFilter() throws IOException {
        Path guide = Path.of("..", "src", "main", "docs", "guide", "httpServer", "gateway.adoc");
        assertTrue(Files.exists(guide), "the guide is at " + guide.toAbsolutePath());
        String text = Files.readString(guide, StandardCharsets.UTF_8);
        String section = text.substring(text.indexOf("[[gatewaySpringSyntax]]"));
        Map<String, String> documented = new TreeMap<>();
        Matcher row = ROW.matcher(section);
        while (row.find()) {
            documented.put(row.group(1) + " " + row.group(2), normalize(row.group(3)));
        }

        Map<String, String> builtIn = new TreeMap<>();
        try (ApplicationContext context = ApplicationContext.run()) {
            for (BeanDefinition<GatewayPredicateFactory> definition : context.getBeanDefinitions(GatewayPredicateFactory.class)) {
                if (definition.getDeclaringType().filter(BuiltInGatewayFactories.class::equals).isPresent()) {
                    String name = definition.stringValue(Named.class).orElseThrow();
                    builtIn.put(name + " predicate", shortcut(context.getBean(definition).shortcutFields()));
                }
            }
            for (BeanDefinition<GatewayFilterFactory> definition : context.getBeanDefinitions(GatewayFilterFactory.class)) {
                if (definition.getDeclaringType().filter(BuiltInGatewayFactories.class::equals).isPresent()) {
                    String name = definition.stringValue(Named.class).orElseThrow();
                    builtIn.put(name + " filter", shortcut(context.getBean(definition).shortcutFields()));
                }
            }
        }
        assertEquals(builtIn.keySet(), documented.keySet(), "the names of the catalog in the guide");
        assertEquals(builtIn, documented, "the shortcut arguments of the catalog in the guide");
    }

    private static String shortcut(java.util.List<String> fields) {
        StringBuilder text = new StringBuilder();
        for (String field : fields) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            // a field of the rest of the arguments, e.g. *patterns, is written patterns...
            text.append(field.startsWith("*") ? field.substring(1) + "..." : field);
        }
        return text.toString();
    }

    private static String normalize(String cell) {
        return cell.strip();
    }
}

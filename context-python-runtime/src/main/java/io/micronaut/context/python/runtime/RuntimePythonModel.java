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
package io.micronaut.context.python.runtime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The versioned compiler/runtime boundary; no compiler AST objects cross it.
 *
 * @param beanType The JVM wrapper name
 * @param singleton Whether to expose a singleton bean definition
 * @param properties The resolved properties
 */
record RuntimePythonModel(String beanType, boolean singleton, List<Property> properties) {
    static final String PATH = "META-INF/micronaut/python/runtime/";

    static MutableAnnotationMetadata metadata(boolean singleton) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Introspected.class.getName(), Map.of("excludes", new String[]{"memberKeys"}));
        if (singleton) {
            metadata.addDeclaredAnnotation(Singleton.class.getName(), Map.of());
        }
        return metadata;
    }

    static RuntimePythonModel read(Class<?> beanType) {
        String resource = PATH + beanType.getName() + ".properties";
        var stream = beanType.getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing Python runtime model: " + resource);
        }
        Properties values = new Properties();
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
        if (!"1".equals(values.getProperty("version")) || !beanType.getName().equals(values.getProperty("bean-type"))) {
            throw new IllegalStateException("Unsupported or mismatched Python runtime model: " + resource);
        }
        List<Property> properties = new ArrayList<>();
        int count = Integer.parseInt(required(values, "property.count"));
        if (count < 0 || count > 10000) {
            throw new IllegalStateException("Invalid property count in " + resource);
        }
        for (int i = 0; i < count; i++) {
            String prefix = "property." + i + ".";
            properties.add(new Property(required(values, prefix + "name"), required(values, prefix + "type"),
                required(values, prefix + "read"), required(values, prefix + "write")));
        }
        String singleton = required(values, "singleton");
        if (!singleton.equals("true") && !singleton.equals("false")) {
            throw new IllegalStateException("Invalid singleton flag in " + resource);
        }
        return new RuntimePythonModel(beanType.getName(), Boolean.parseBoolean(singleton), List.copyOf(properties));
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing runtime model member: " + key);
        }
        return value;
    }

    record Property(String name, String type, String read, String write) {
        Class<?> javaType() {
            return switch (type) {
                case "java.lang.String" -> String.class;
                case "int" -> int.class;
                case "boolean" -> boolean.class;
                default -> throw new IllegalStateException("Unsupported runtime property type: " + type);
            };
        }
    }
}

/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.config.importer;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceImporter;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.util.ConnectionString;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoPropertySourceImporterTest {

    // tag::test[]
    @Test
    void importsDemoDefaults() {
        try (ApplicationContext context = ApplicationContext.run()) {
            DemoPropertySourceImporter importer = new DemoPropertySourceImporter();
            DemoPropertySourceImporter.DemoImport declaration = importer.newImportDeclaration(ConnectionString.parse("demo://defaults"));
            PropertySourceImporter.ImportContext<DemoPropertySourceImporter.DemoImport> importContext = new PropertySourceImporter.ImportContext<>() {
                @Override
                public Environment environment() {
                    return context.getEnvironment();
                }

                @Override
                public ConnectionString connectionString() {
                    return ConnectionString.parse("demo://defaults");
                }

                @Override
                public DemoPropertySourceImporter.DemoImport importDeclaration() {
                    return declaration;
                }

                @Override
                public PropertySource.Origin parentOrigin() {
                    return PropertySource.Origin.of("classpath:application.yml");
                }

                @Override
                public Optional<PropertySource> importPropertySource(ResourceLoader resourceLoader,
                                                                     String resourcePath,
                                                                     String sourceName,
                                                                     PropertySource.Origin origin) {
                    return Optional.empty();
                }

                @Override
                public Optional<PropertySource> importPropertySource(String content,
                                                                     String sourceName,
                                                                     String extension,
                                                                     PropertySource.Origin origin) {
                    return Optional.empty();
                }

                @Override
                public Optional<PropertySource> importClasspathPropertySource(String resourcePath,
                                                                              String sourceName,
                                                                              PropertySource.Origin origin,
                                                                              boolean allowMultiple) {
                    return Optional.empty();
                }
            };

            Optional<PropertySource> propertySource = importer.importPropertySource(importContext);

            assertTrue(propertySource.isPresent());
            assertEquals("hello-from-demo-importer", propertySource.get().get("demo.message"));
        }
    }
    // end::test[]
}

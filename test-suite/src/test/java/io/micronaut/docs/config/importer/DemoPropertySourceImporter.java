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

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceImporter;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ConnectionString;

import java.util.Map;
import java.util.Optional;

// tag::class[]
public final class DemoPropertySourceImporter implements PropertySourceImporter<DemoPropertySourceImporter.DemoImport> {

    @Override
    public String getProvider() {
        return "demo";
    }

    @Override
    public DemoImport newImportDeclaration(ConnectionString connectionString) {
        return new DemoImport(connectionString.getPath());
    }

    @Override
    public DemoImport newImportDeclaration(ConvertibleValues<Object> values) {
        return new DemoImport(values.get("path", String.class).orElse("defaults"));
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext<DemoImport> context) {
        if (!"defaults".equals(context.importDeclaration().path())) {
            return Optional.empty();
        }
        return Optional.of(PropertySource.of(
            "demo:defaults",
            Map.of("demo.message", "hello-from-demo-importer")
        ));
    }

    public record DemoImport(String path) {
    }
}
// end::class[]

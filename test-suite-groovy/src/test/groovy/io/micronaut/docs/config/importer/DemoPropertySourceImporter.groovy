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
package io.micronaut.docs.config.importer

import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourceImporter
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.util.ConnectionString

// tag::class[]
class DemoPropertySourceImporter implements PropertySourceImporter<DemoPropertySourceImporter.DemoImport> {

    @Override
    String getProvider() {
        return "demo"
    }

    @Override
    DemoImport newImportDeclaration(ConnectionString connectionString) {
        new DemoImport(connectionString.path)
    }

    @Override
    DemoImport newImportDeclaration(ConvertibleValues<Object> values) {
        new DemoImport(values.get("path", String).orElse("defaults"))
    }

    @Override
    Optional<PropertySource> importPropertySource(PropertySourceImporter.ImportContext<DemoImport> context) {
        if (context.importDeclaration().path() != "defaults") {
            return Optional.empty()
        }
        return Optional.of(PropertySource.of("demo:defaults", ["demo.message": "hello-from-demo-importer"]))
    }

    static record DemoImport(String path) {
    }
}
// end::class[]

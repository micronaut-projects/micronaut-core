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
class DemoPropertySourceImporter : PropertySourceImporter<DemoPropertySourceImporter.DemoImport> {
    override fun getProvider(): String = "demo"

    override fun newImportDeclaration(connectionString: ConnectionString): DemoImport = DemoImport(connectionString.path)

    override fun newImportDeclaration(values: ConvertibleValues<Any>): DemoImport = DemoImport(values.get("path", String::class.java).orElse("defaults"))

    override fun importPropertySource(context: PropertySourceImporter.ImportContext<DemoImport>): java.util.Optional<PropertySource> {
        if (context.importDeclaration().path != "defaults") {
            return java.util.Optional.empty()
        }
        return java.util.Optional.of(
            PropertySource.of(
                "demo:defaults",
                mapOf("demo.message" to "hello-from-demo-importer")
            )
        )
    }

    data class DemoImport(val path: String)
}
// end::class[]

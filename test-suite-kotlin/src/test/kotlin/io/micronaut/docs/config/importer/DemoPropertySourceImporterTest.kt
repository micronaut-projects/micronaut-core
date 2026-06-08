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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourceImporter
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.util.ConnectionString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DemoPropertySourceImporterTest {

    // tag::test[]
    @Test
    fun importsDemoDefaults() {
        ApplicationContext.run().use { context ->
            val importer = DemoPropertySourceImporter()
            val declaration = importer.newImportDeclaration(ConnectionString.parse("demo://defaults"))
            val importContext = object : PropertySourceImporter.ImportContext<DemoPropertySourceImporter.DemoImport> {
                override fun environment(): Environment = context.environment

                override fun connectionString(): ConnectionString = ConnectionString.parse("demo://defaults")

                override fun importDeclaration(): DemoPropertySourceImporter.DemoImport = declaration

                override fun parentOrigin(): PropertySource.Origin = PropertySource.Origin.of("classpath:application.yml")

                override fun importPropertySource(
                    resourceLoader: ResourceLoader,
                    resourcePath: String,
                    sourceName: String,
                    origin: PropertySource.Origin
                ): java.util.Optional<PropertySource> = java.util.Optional.empty()

                override fun importPropertySource(
                    content: String,
                    sourceName: String,
                    extension: String,
                    origin: PropertySource.Origin
                ): java.util.Optional<PropertySource> = java.util.Optional.empty()

                override fun importClasspathPropertySource(
                    resourcePath: String,
                    sourceName: String,
                    origin: PropertySource.Origin,
                    allowMultiple: Boolean
                ): java.util.Optional<PropertySource> = java.util.Optional.empty()
            }

            val propertySource = importer.importPropertySource(importContext)

            assertTrue(propertySource.isPresent)
            assertEquals("hello-from-demo-importer", propertySource.get().get("demo.message"))
        }
    }
    // end::test[]
}

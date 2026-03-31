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
import spock.lang.Specification

class DemoPropertySourceImporterSpec extends Specification {

    // tag::test[]
    void "imports demo defaults"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        DemoPropertySourceImporter importer = new DemoPropertySourceImporter()
        DemoPropertySourceImporter.DemoImport declaration = importer.newImportDeclaration(ConnectionString.parse("demo://defaults"))
        PropertySourceImporter.ImportContext<DemoPropertySourceImporter.DemoImport> importContext = new PropertySourceImporter.ImportContext<DemoPropertySourceImporter.DemoImport>() {
            @Override
            Environment environment() {
                context.environment
            }

            @Override
            ConnectionString connectionString() {
                ConnectionString.parse("demo://defaults")
            }

            @Override
            DemoPropertySourceImporter.DemoImport importDeclaration() {
                declaration
            }

            @Override
            PropertySource.Origin parentOrigin() {
                PropertySource.Origin.of("classpath:application.yml")
            }

            @Override
            Optional<PropertySource> importPropertySource(ResourceLoader resourceLoader,
                                                          String resourcePath,
                                                          String sourceName,
                                                          PropertySource.Origin origin) {
                Optional.empty()
            }

            @Override
            Optional<PropertySource> importPropertySource(String content,
                                                          String sourceName,
                                                          String extension,
                                                          PropertySource.Origin origin) {
                Optional.empty()
            }

            @Override
            Optional<PropertySource> importClasspathPropertySource(String resourcePath,
                                                                   String sourceName,
                                                                   PropertySource.Origin origin,
                                                                   boolean allowMultiple) {
                Optional.empty()
            }
        }

        when:
        Optional<PropertySource> propertySource = importer.importPropertySource(importContext)

        then:
        propertySource.present
        propertySource.get().get("demo.message") == "hello-from-demo-importer"

        cleanup:
        context.close()
    }
    // end::test[]
}

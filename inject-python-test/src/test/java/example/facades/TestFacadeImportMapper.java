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
package example.facades;

import io.micronaut.python.imports.PythonImportMapper;
import io.micronaut.python.imports.PythonModuleMapping;
import io.micronaut.python.imports.PythonModuleMapping.ClashPolicy;

import java.util.List;

/**
 * Curated modules the facade specs import, under {@code facadetest}.
 */
public final class TestFacadeImportMapper implements PythonImportMapper {

    @Override
    public List<PythonModuleMapping> getMappings() {
        return List.of(
            PythonModuleMapping.builder("facadetest.http")
                .documentation("HTTP routing, requests and responses.")
                .javaPackage("io.micronaut.http.annotation")
                .javaPackage("io.micronaut.http")
                .staticMethods("io.micronaut.http.HttpResponse")
                .constants("io.micronaut.http.HttpStatus")
                .constants("io.micronaut.http.HttpMethod")
                .constants("io.micronaut.http.MediaType", "APPLICATION_JSON", "TEXT_PLAIN")
                .build(),
            PythonModuleMapping.builder("facadetest.http.client")
                .javaPackage("io.micronaut.http.client.annotation")
                .javaPackage("io.micronaut.http.client")
                .javaPackage("io.micronaut.http.client.exceptions")
                .staticMethods("io.micronaut.http.HttpRequest")
                .build(),
            PythonModuleMapping.builder("facadetest.inject")
                .javaPackage("jakarta.inject")
                .javaPackage("io.micronaut.context.annotation")
                .javaPackage("io.micronaut.context")
                .clashPolicy(ClashPolicy.PREFER_ANNOTATIONS)
                .build(),
            PythonModuleMapping.builder("facadetest.validation")
                .javaPackage("jakarta.validation.constraints")
                .javaPackage("jakarta.validation")
                .javaPackage("io.micronaut.validation.validator")
                .prefer("Validator", "io.micronaut.validation.validator.Validator")
                .build(),
            PythonModuleMapping.builder("facadetest.missing")
                .javaPackage("io.micronaut.does.not.exist")
                .requiredArtifact("io.micronaut.example:does-not-exist")
                .build()
        );
    }
}

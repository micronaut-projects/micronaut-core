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
package io.micronaut.context.env;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ConnectionString;

/**
 * Imports and merges property sources from all matching classpath locations.
 */
public final class ClasspathWildcardPropertySourceImporter extends ClasspathPropertySourceImporter {

    @Override
    public String getProvider() {
        return "classpath*";
    }

    @Override
    public ClasspathImport newImportDeclaration(ConnectionString connectionString) {
        return new ClasspathImport(connectionString.getPath(), true);
    }

    @Override
    public ClasspathImport newImportDeclaration(ConvertibleValues<Object> values) {
        ClasspathImport declaration = super.newImportDeclaration(values);
        return new ClasspathImport(declaration.resourcePath(), true);
    }
}

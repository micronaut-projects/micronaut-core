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
package io.micronaut.python.annotation.processing.test.constants;

import io.micronaut.context.python.annotation.PythonClass;

/**
 * Stands in for the Java class generated for a Python class of another compilation (the main
 * sources seen from the tests): it carries the marker annotation but no field for the class
 * attributes of the Python class.
 */
@PythonClass(packageName = "python", rootName = "GeneratedPaths", displayName = "GeneratedPaths", cacheKey = "python.GeneratedPaths")
public final class GeneratedPaths {

    private GeneratedPaths() {
    }
}

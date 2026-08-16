/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.python.annotation;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a Java stub generated for a Python module.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Documented
@Internal
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PythonModule {
    /**
     * @return The original Python module name; file-backed modules include their {@code .py} suffix
     */
    String moduleName();

    /**
     * @return The Python package containing the module
     */
    String packageName();
}

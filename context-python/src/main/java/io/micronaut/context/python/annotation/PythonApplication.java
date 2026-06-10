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
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to enable Python AST processing within a Java annotation processor.
 * This annotation allows inlining Python code or scanning directories for Python files
 * that will be processed during compilation.
 *
 * @since 5.0.0
 * @author Micronaut
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
@Internal
@Experimental
public @interface PythonApplication {

    /**
     * Inline Python source code to process.
     *
     * @return Inline Python source code.
     */
    String code() default "";

    /**
     * Source paths containing Python files to process.
     *
     * @return Source paths containing Python files.
     */
    String[] src() default "";
}

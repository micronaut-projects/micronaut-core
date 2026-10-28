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
package io.micronaut.context.python.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Switches the static compilation of Python function bodies on or off for a class, a function or,
 * applied as a bare statement, a module. The nearest declaration wins: a function over its class, a
 * class over its module, a module over the compilation's mode. A scope switched on under mode
 * {@code off} is compiled; a function switched on that cannot be compiled is reported.
 *
 * <p>The annotation is kept in the source only and leaves no trace in the generated classes.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Documented
@Experimental
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CompileStatic {

    /**
     * @return Whether the annotated scope is compiled statically
     */
    boolean value() default true;
}

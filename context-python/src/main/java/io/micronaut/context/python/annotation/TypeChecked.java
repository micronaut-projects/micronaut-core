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
 * Switches the compile-time type checking of Python code on or off for a class, a function or,
 * applied as a bare statement, a module.
 *
 * <pre>{@code
 * from micronaut.context.python.annotation import TypeChecked
 *
 * @Singleton
 * @TypeChecked(False)          # the whole class is left unchecked
 * class LegacyAdapter: ...
 *
 * @Singleton
 * class OrderService:
 *     @TypeChecked              # checked even when the compilation does not check by default
 *     def place(self, order: Order) -> Receipt: ...
 * }</pre>
 *
 * <p>The nearest declaration wins: a function over its class, a class over its module, a module
 * over the compilation's {@code micronaut.python.typecheck} mode. The annotation is kept in the
 * source only; it leaves no trace in the generated classes.</p>
 *
 * @since 5.3.0
 */
@Documented
@Experimental
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TypeChecked {

    /**
     * @return Whether the annotated scope is type checked
     */
    boolean value() default true;
}

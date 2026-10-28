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

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a generated class whose statically compiled body never enters Python: nothing
 * in it does, and every method of the compilation it calls is compiled and never enters Python in
 * turn. The Python processor adds it; {@code PythonExecutorSelector} then selects the executor of
 * the method as it does for a Java method, so a route stays on the event loop unless it is
 * annotated with {@code @ExecuteOn}.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Documented
@Internal
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventLoopSafe {
}

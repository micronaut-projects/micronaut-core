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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.MODULE;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks the annotated declaration and all code transitively enclosed by it as <b>null-marked</b>:
 * within that scope, type usages are non-null by default unless explicitly annotated as
 * {@code @Nullable}.
 *
 * <p>This annotation is intentionally similar in purpose and semantics to
 * {@code org.jspecify.annotations.NullMarked}. It provides an opt-in, "non-null by default" mode so
 * you don't have to repeat a non-null annotation on every type usage.
 *
 * <p>Where it can be used:
 * <ul>
 *   <li>Module declarations (before the {@code module} keyword)</li>
 *   <li>Packages (via {@code package-info.java})</li>
 *   <li>Types (classes, interfaces, records, enums)</li>
 *   <li>Methods</li>
 *   <li>Constructors</li>
 * </ul>
 *
 * <p>Behavior notes:
 * <ul>
 *   <li>The effect applies transitively to enclosed declarations.</li>
 *   <li>Elements can opt out by explicitly using {@code @Nullable} on type usages, or with a
 *       counterpart such as {@code @NullUnmarked} if available.</li>
 *   <li>Some language constructs (e.g., wildcards and type variables) may need explicit nullness
 *       annotations to express intent clearly.</li>
 * </ul>
 *
 * @author Denis Stepanov
 * @see 4.10
 */
@Documented
@Target({MODULE, PACKAGE, TYPE, METHOD, CONSTRUCTOR})
@Retention(RUNTIME)
public @interface NullMarked {
}

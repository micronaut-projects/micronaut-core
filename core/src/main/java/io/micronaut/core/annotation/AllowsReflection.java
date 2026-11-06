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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A hint that a framework reads the runtime annotations of a declaration reflectively, from the Java
 * class rather than through the Micronaut annotation metadata, so that a compiler generating the Java
 * class for a declaration written in another language copies its reflection data onto the generated
 * class. The Python compiler ({@code micronaut-inject-python}) copies the runtime annotations of a
 * Python class, of its members and of the parameters of its methods onto the generated Java type when the
 * class carries this hint, whatever the patterns of the {@code micronaut.introspection.allow-reflection}
 * option allow.
 *
 * <p>The hint is a stereotype: it applies to a declaration that carries it, to every declaration
 * annotated with an annotation type that is itself annotated with it, and to a declaration whose
 * annotation an annotation mapper maps to it. A framework integration that reads its own annotations
 * reflectively therefore declares it once, on its annotation type or in its annotation mapper (an AI
 * service annotation read by {@code java.lang.reflect.Proxy}, a JPA {@code @Entity} read by Hibernate),
 * and its users need no configuration.</p>
 *
 * @since 5.2.3
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
@Experimental
public @interface AllowsReflection {
}

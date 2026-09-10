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
/**
 * Reflective implementations of the metadata the annotation processors generate at compilation time, for the
 * types they never saw.
 *
 * <p>The framework describes a type through {@link io.micronaut.core.annotation.AnnotationMetadata},
 * {@link io.micronaut.core.type.Argument}, {@link io.micronaut.inject.ExecutableMethod},
 * {@link io.micronaut.core.beans.BeanIntrospection} and {@link io.micronaut.inject.BeanDefinition}, all of them
 * generated when the type is compiled. A specification that has to handle any class - Jakarta Validation, Jakarta
 * REST, CDI - meets types that were not compiled with the processors, and has to describe them through
 * {@code java.lang.reflect} instead. This package builds the same metadata from reflection, in the shape the
 * processors give it, so that code written against generated metadata works unchanged:</p>
 *
 * <ul>
 *     <li>{@link io.micronaut.reflection.ReflectionAnnotations} - the annotation metadata of an element, with the
 *     stereotypes, the repeatable containers and the defaults the processors record;</li>
 *     <li>{@link io.micronaut.reflection.ReflectionArguments} - the arguments of the parameters, fields and return
 *     types, with the type-use annotations of every level;</li>
 *     <li>{@link io.micronaut.reflection.ReflectionExecutableMethod} and
 *     {@link io.micronaut.reflection.ReflectionBeanConstructor} - an executable over a {@link java.lang.reflect.Method}
 *     or a {@link java.lang.reflect.Constructor}, and {@link io.micronaut.reflection.ReflectionExecutables} to resolve
 *     one to the best metadata available;</li>
 *     <li>{@link io.micronaut.reflection.ReflectionBeanIntrospection} - an introspection over a class, and
 *     {@link io.micronaut.reflection.ReflectionBeanIntrospector} to serve the generated ones first;</li>
 *     <li>{@link io.micronaut.reflection.ReflectionBeanDefinition} - a bean definition over a class, registered at
 *     runtime, with the injection points and the life cycle of a generated one;</li>
 *     <li>{@link io.micronaut.reflection.MethodHierarchy} - what each level of a method hierarchy declares.</li>
 * </ul>
 *
 * <p>Every type of this package is {@link io.micronaut.core.annotation.Experimental}: reading a class back is what
 * the framework is built to avoid, and an application says it wants it by depending on this module and, for the
 * shared introspector, by allowing the types through
 * {@link io.micronaut.reflection.ReflectionIntrospectionPolicy}.</p>
 *
 * @since 5.2.0
 */
@NullMarked
package io.micronaut.reflection;

import org.jspecify.annotations.NullMarked;

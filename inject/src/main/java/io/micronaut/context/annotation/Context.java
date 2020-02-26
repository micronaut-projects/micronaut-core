/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/**
 * <p>Context scope indicates that the classes life cycle is bound to that of the
 * {@link io.micronaut.context.BeanContext} and it should be initialized and shutdown during startup and shutdown of
 * the underlying {@link io.micronaut.context.BeanContext}.</p>
 * <p>
 * <p>Micronaut by default treats all {@link Singleton} bean definitions as lazy and will only load them on demand.  By
 * annotating a bean with @Context you can ensure that the bean is loaded at the same time as the context.</p>
 * <p>
 * <p>WARNING: This annotation should be used sparingly as Micronaut is designed in such a way as to encourage minimal
 * bean creation during startup.</p>
 * <p>
 * <p>NOTE: This annotation can also be used as a meta annotation</p>
 *
 * @see Singleton @Singleton
 */
@Singleton
@Documented
@Retention(RUNTIME)
public @interface Context {
}

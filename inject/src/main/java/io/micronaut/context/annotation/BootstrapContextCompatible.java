/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation indicating that a bean can be loaded into the Bootstrap Context. The Bootstrap Context is a
 * small {@link io.micronaut.context.ApplicationContext} instance that is loaded in order to support reading
 * distributed configuration.
 *
 * <p>Most beans are not required in this context and only a subset may be needed in order to perform the task of
 * reading distributed configuration. Hence any bean that wishes to be loaded into the context must express that they can be loaded using this annotation.</p>
 *
 * <p>Most developers generally don't have to interact with this annotation and it is generally only used when building a new distributed configuration implementation.</p>
 */
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface BootstrapContextCompatible {
}

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

import javax.inject.Scope;
import java.lang.annotation.Retention;

/**
 * <p>Provided scope is used to define a bean that should not be considered a candidate for dependency injection because
 * it is provided by another bean. This scope is used when, for example, you have a factory bean that returns a bean
 * that also requires dependency injection.</p>
 *
 * @author Graeme Rocher
 * @see Bean
 * @see Factory
 * @since 1.0
 */
@Scope
@Retention(RUNTIME)
public @interface Provided {
}

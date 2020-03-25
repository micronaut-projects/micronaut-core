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
package io.micronaut.http.annotation;

import io.micronaut.http.MediaType;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>An annotation to indicate the {@link io.micronaut.http.MediaType}s produced by a particular component.</p>
 * <p>
 * <p>Generally with controllers one can use the {@code consumes} attribute of the {@code Controller} annotation,
 * however this annotation is more generic and applies to any component</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Consumes {

    /**
     * @return The {@link io.micronaut.http.MediaType} values that this component is able to consume
     */
    String[] value() default MediaType.APPLICATION_JSON;

    /**
     * <p>Applies to clients that return reactive types.</p>
     * <p>
     * <p>This member indicates whether the response handling should stream or wait until
     * the full response is read. Normally this annotation is unnecessary unless the declared type doesn't indicate
     * how many items are emitted.</p>
     *
     * @return True if only a single result is emitted
     */
    boolean single() default false;
}

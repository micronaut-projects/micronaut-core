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
package io.micronaut.http.client.annotation;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The timeouts of the exchanges of a declarative client method, or of every method of a
 * declarative client, instead of the timeouts of the client configuration, see
 * {@link io.micronaut.http.client.RequestTimeouts}. A timeout set on a method overrides the same
 * timeout set on its client, and the timeouts the method does not set are taken from the client.
 * The values are durations, e.g. {@code 30s} or {@code 2m}, and can be property placeholders. A
 * timeout set on neither is taken from the client configuration.
 *
 * @author Adam Kobor
 * @since 5.3.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Bindable
@Experimental
public @interface ClientTimeouts {

    /**
     * @return The read timeout, see {@link io.micronaut.http.client.RequestTimeouts#getReadTimeout()}
     */
    String read() default "";

    /**
     * @return The request timeout, see {@link io.micronaut.http.client.RequestTimeouts#getRequestTimeout()}
     */
    String request() default "";
}

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
package io.micronaut.http.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Executable;
import io.micronaut.http.HttpMethod;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>An annotation that can be applied to classes that implement {@link io.micronaut.http.filter.HttpFilter} to
 * specify the patterns.</p>
 * <p>
 * <p>Used as an alternative to applying filters manually via the {code Router} API</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Executable
public @interface Filter {

    /**
     * Pattern used to match all requests.
     */
    String MATCH_ALL_PATTERN = "/**";

    /**
     * @return The patterns this filter should match
     */
    String[] value() default {};

    /**
     * Same as {@link #value()}.
     *
     * @return The patterns
     */
    @AliasFor(member = "value")
    String[] patterns() default {};

    /**
     * @return The methods to match. Defaults to all
     */
    HttpMethod[] methods() default {};

    /**
     * The service identifiers this filter applies to. Currently, applies only to {@link io.micronaut.http.filter.HttpClientFilter} instances.
     * Equivalent to the {@code id()} of {@code io.micronaut.http.client.Client}.
     *
     * @return The service identifiers
     */
    String[] serviceId() default {};
}

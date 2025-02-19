/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.filter.FilterPatternStyle;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Mark a bean as a filter for the HTTP server. The bean may declare {@link RequestFilter}s and
 * {@link ResponseFilter}s.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Bean
@DefaultScope(Singleton.class)
public @interface ServerFilter {
    /**
     * Pattern used to match all requests.
     */
    String MATCH_ALL_PATTERN = Filter.MATCH_ALL_PATTERN;

    /**
     * @return The patterns this filter should match
     */
    String[] value() default {};

    /**
     * @return The style of pattern this filter uses
     */
    FilterPatternStyle patternStyle() default FilterPatternStyle.ANT;

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
     * @return Whether the contextPath should be concatenated into the filter pattern
     * @since 4.5.1
     */
    boolean appendContextPath() default true;
}

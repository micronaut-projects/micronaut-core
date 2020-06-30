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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.MediaType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>An annotation to indicate the {@link MediaType}s produced by a particular component.</p>
 * <p>
 * <p>Generally with controllers one can use the {@code produces} attribute of the {@code Controller} annotation,
 * however this annotation is more generic and applies to any component</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Produces {

    /**
     * @return The {@link MediaType} values that this component is able to produce
     */
    String[] value() default MediaType.APPLICATION_JSON;

    /**
     * <p>In the case of reactive types this member indicates whether only a single result is returned. Normally this
     * annotation is unnecessary unless the declared type doesn't indicate how many items are emitted.</p>
     *
     * <p>For JSON with single=false if an Reactive streams Publisher type is returned these will be automatically
     * wrapped in an Array type to ensure valid JSON is returned.</p>
     *
     * <p>If single=true it is expected that only a single result will be emitted and the result will not be wrapped
     * in a JSON array.</p>
     *
     * @return True if only a single result is emitted
     */
    @AliasFor(annotation = SingleResult.class, member = "value")
    boolean single() default false;
}

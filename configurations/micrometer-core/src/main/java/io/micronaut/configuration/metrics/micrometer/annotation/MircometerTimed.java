/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.metrics.micrometer.annotation;

import io.micronaut.aop.Around;
import io.micronaut.configuration.metrics.micrometer.intercept.TimedInterceptor;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.*;

/**
 * This is an internal annotation used to trigger AOP for {@link io.micrometer.core.annotation.Timed}.
 *
 * <p>This annotation should not be used directly in user code, instead {@link io.micrometer.core.annotation.Timed} should be used</p>
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Internal
@Around
@Type(TimedInterceptor.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Inherited
public @interface MircometerTimed {
}

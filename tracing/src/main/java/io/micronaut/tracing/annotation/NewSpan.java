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
package io.micronaut.tracing.annotation;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Type;
import io.micronaut.tracing.interceptor.TraceInterceptor;

import java.lang.annotation.*;

/**
 * <p>Indicates that a new Open Tracing span should be started.</p>
 *
 * <p>Annotation Inspired by Spring Sleuth but using Open Tracing and Micronaut AOP</p>
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(value = { ElementType.METHOD })
@Around
@Type(TraceInterceptor.class)
public @interface NewSpan {

    /**
     * The name of the span which will be created. Default is the annotated method's name separated by hyphens.
     *
     * @return String The key to use for the tag
     */
    String value() default "";
}

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
package io.micronaut.runtime.context.scope;

import jakarta.inject.Scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A {@link io.micronaut.context.scope.CustomScope} that stores objects in thread local storage.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ScopedProxy
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Scope
public @interface ThreadLocal {
    /**
     * If enabled, track bean life cycle. This means that the bean will be stopped, destroy
     * listeners will be called etc., when the application context is closed or when the associated
     * thread terminates. Note that this adds some overhead, so it's off by default.
     *
     * @return Whether to enable lifecycle support for this bean
     * @since 4.9.0
     */
    boolean lifecycle() default false;
}

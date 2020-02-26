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
package io.micronaut.aop;

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Introduction advice allows interfaces and abstract classes to be implemented at compile time by
 * {@link MethodInterceptor} implementations.</p>
 *
 * <p>This annotation should be applied as a meta annotation to another annotation that references the {@link MethodInterceptor} by type</p>
 *
 * <p>For example:</p>
 *
 * <pre><code>
 *  {@literal @}Introduction
 *  {@literal @}Type(ExampleIntroduction.class)
 *  {@literal @}Documented
 *  {@literal @}Retention(RUNTIME)
 *   public @interface Example {
 *   }
 * </code></pre>
 *
 * <p>Note that the annotation MUST be {@link java.lang.annotation.RetentionPolicy#RUNTIME} and the specified {@link io.micronaut.context.annotation.Type} must implement {@link MethodInterceptor}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Executable
public @interface Introduction {

    /**
     * Additional interfaces that the introduction advice should implement. Note that if introduction advise is applied
     * to a concrete class at least 1 interface must be specified
     *
     * @return The additional interfaces to implement
     */
    Class[] interfaces() default {};
}

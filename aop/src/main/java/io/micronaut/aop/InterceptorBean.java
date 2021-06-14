/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.aop;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import jakarta.inject.Singleton;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A meta-annotation that can be used on {@link MethodInterceptor} declarations to specify
 * the {@link InterceptorBinding} and make the type a bean.
 *
 * @author graemerocher
 * @since 2.4.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Bean
@DefaultScope(Singleton.class)
public @interface InterceptorBean {
    /**
     * The value of this annotation can be used to indicate the annotations the
     * {@link MethodInterceptor} binds to at runtime.
     *
     * @return The annotation type the interceptor binds to.
     */
    Class<? extends Annotation>[] value();
}


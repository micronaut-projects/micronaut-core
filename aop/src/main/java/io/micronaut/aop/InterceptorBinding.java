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

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An {@code InterceptorBinding} is used as a meta-annotation on {@link Around} and {@link Introduction} advice to
 * indicate that AOP advice should be applied to the method and that any annotations that feature this stereotype annotation
 * should be used to resolve associated interceptors at runtime.
 *
 * @author graemerocher
 * @since 2.4.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Repeatable(InterceptorBindingDefinitions.class)
public @interface InterceptorBinding {
    /**
     * When declared on an interceptor, the value of this annotation can be used to indicate the annotation the
     * {@link MethodInterceptor} binds to at runtime.
     *
     * @return The annotation type the interceptor binds to.
     */
    Class<? extends Annotation> value() default Annotation.class;

    /**
     * @return The kind of interceptor.
     */
    InterceptorKind kind() default InterceptorKind.AROUND;
}

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
package io.micronaut.context.annotation;

import io.micronaut.core.bind.annotation.Bindable;

import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Specifies that an argument to a bean constructor is user provided and a
 * {@link io.micronaut.inject.ParametrizedBeanFactory} should be generated.</p>
 * <p>
 * <p>Should be applied only to constructor arguments and {@link Bean} factory methods</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
@Bindable
public @interface Parameter {

    /**
     * Specifies the parameter name. Useful as metadata at times for reflection on classes already compiled
     * without -parameters argument to javac
     *
     * @return An optional name of the parameter.
     */
    @AliasFor(annotation = Bindable.class, member = "value")
    String value() default "";
}

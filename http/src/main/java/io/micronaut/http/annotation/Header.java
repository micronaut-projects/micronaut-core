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

package io.micronaut.http.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.*;

/**
 * An annotation that can be applied to method argument to indicate that the method argument is bound from an HTTP header
 * This also can be used in conjection with @Headers to list headers on a client class that will always be applied.
 * Example on a class:
 *
 *
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.PARAMETER, ElementType.TYPE}) // this can be either type or param
@Repeatable(value = Headers.class)
@Bindable
public @interface Header {

    /**
     * If used as a bound parameter, this is the header name. If used on a class level this is value and not the header name.
     * @return The name of the header, otherwise it is inferred from the parameter name
     */
    String value() default "";

    /**
     * If used on a class level with @Headers this is the header name and value is the value.
     * @return name of header when using with @Headers
     */
    String name() default "";

}

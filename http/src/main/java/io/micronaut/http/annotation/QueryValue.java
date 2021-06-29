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
import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that the parameter to a method should be bound from a value in the query string or path of the URI.
 *
 * @author Graeme Rocher
 * @see java.net.URI#getQuery()
 * @see java.net.URI#getPath()
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Bindable
@Inherited
public @interface QueryValue {

    /**
     * @return The name of the parameter
     */
    @AliasFor(annotation = Bindable.class, member = "value")
    String value() default "";

    /**
     * @see Bindable#defaultValue()
     * @return The default value
     */
    @AliasFor(annotation = Bindable.class, member = "defaultValue")
    String defaultValue() default "";

    /**
     * @return The format of the given values in the URL
     */
    Format format() default Format.COMMA_DELIMITED;

    /**
     * The possible formats of the query parameter in the URL.
     * The conversion of various types is according to openapi v3 specification:
     * @see <a href="https://swagger.io/specification/">openapi v3 specification</a>
     * Values are serialized using Jackson object mapper
     */
    public static enum Format {
        /**
         * The values of iterator are comma-delimited.
         * The value for object will contain separated attribute names and values, e.g.: 'color=R,100,G,200,B,150'.
         */
        COMMA_DELIMITED,
        /**
         * The values are space-delimited, similarly to comma-delimited format.
         * If possible, it is recommended to use a different format, as space is not a reserved symbol by
         * <a href="https://datatracker.ietf.org/doc/html/rfc3986#section-2.3">rfc3986</a> and thus ambiguity arises
         * if string values contain spaces themselves.
         */
        SPACE_DELIMITED,
        /**
         * The values a delimited by pipes "|", similarly to comma-delimited format.
         * This is also not a recommended format for the same reasons as SPACE_DELIMITED
         */
        PIPE_DELIMITED,
        /**
         * The values are repeated as separate parameters, e.g.: color=blue&color=black&color=brown.
         * The value for object will contain its keys as separate parameters, e.g.: 'R=100&G=200&B=150'.
         */
        MULTI,
        /**
         * The format supports recursion into objects with setting each attribute as a separate parameter, e.g.:
         * 'color[R]=100&color[G]=200&color[B]=150'.
         */
        DEEP_OBJECT
    }
}

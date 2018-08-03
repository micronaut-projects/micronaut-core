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

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;
import io.micronaut.http.MediaType;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>Indicates that the role of a class is a controller within an application.</p>
 * <p>
 * <p>By default all public methods of a controller are considered {@link Executable} and
 * the necessary classes generated to perform the invocation.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Bean
@Executable
@DefaultScope(Singleton.class)
public @interface Controller {

    /**
     * <p>This attribute returns the base URI of the controller. If not specified the URI will be
     * formulated from the controller class name without the "Controller" suffix (ie. {@code MessageController becomes}
     * {@code /message}).</p>
     * <p>
     * <p>A value of {@code /} can be used to map a controller
     * to the root URI.</p>
     *
     * @return The base URI of the controller in the case of web applications
     */
    String value();

    /**
     * @return The produced MediaType values. Defaults to application/json
     */
    @AliasFor(annotation = Produces.class, member = "value")
    String[] produces() default MediaType.APPLICATION_JSON;

    /**
     * @return The consumed MediaType for request bodies Defaults to application/json
     */
    @AliasFor(annotation = Consumes.class, member = "value")
    String[] consumes() default MediaType.APPLICATION_JSON;
}

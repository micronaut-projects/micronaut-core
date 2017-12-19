/*
 * Copyright 2017 original authors
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
package org.particleframework.http.annotation;

import org.particleframework.context.annotation.AliasFor;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Executable;
import org.particleframework.http.MediaType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Indicates that the role of a class is a controller within an application.</p>
 *
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
public @interface Controller {
    /**
     * @return The base URI of the controller in the case of web applications
     */
    String value() default "";

    @AliasFor(member = "value")
    String uri() default "";

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

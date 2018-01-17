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
import org.particleframework.context.annotation.Executable;
import org.particleframework.http.annotation.Controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>A meta annotation for HTTP {@link Controller} actions</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
@Executable
public @interface HttpMethodMapping {

    /**
     * @return The URI of the action if not specified inferred from the method name and arguments
     */
    String value() default "";

    /**
     * @return The URI of the PATCH route if not specified inferred from the method name and arguments
     */
    @AliasFor(member = "value")
    String uri() default "";

}

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
package org.particleframework.management.endpoint;


import org.particleframework.context.annotation.AliasFor;
import org.particleframework.context.annotation.ConfigurationReader;
import org.particleframework.context.annotation.Executable;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines a management endpoint for a given ID
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@ConfigurationReader
@Executable
public @interface Endpoint {

    /**
     * @return The ID of the endpoint
     */
    @AliasFor(annotation = ConfigurationReader.class, member = "value")
    String value();

    /**
     * @return The ID of the endpoint
     */
    @AliasFor(member = "value")
    String id();
    /**
     * @return The default prefix to use
     */
    @AliasFor(annotation = ConfigurationReader.class, member = "prefix")
    String prefix() default "endpoints";
}

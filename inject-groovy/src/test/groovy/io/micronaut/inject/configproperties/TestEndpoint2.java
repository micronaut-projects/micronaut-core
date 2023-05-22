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
package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.ConfigurationReader;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@ConfigurationReader(prefix = "endpoints")
public @interface TestEndpoint2 {
    /**
     * @return The ID of the endpoint
     */
    @AliasFor(annotation = ConfigurationReader.class, member = ConfigurationReader.PREFIX)
    @AliasFor(member = "id")
    String value() default "";

    /**
     * @return The ID of the endpoint
     */
    @AliasFor(member = "value")
    @AliasFor(annotation = ConfigurationReader.class, member = ConfigurationReader.PREFIX)
    String id() default "";

}

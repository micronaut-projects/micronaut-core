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
package io.micronaut.management.endpoint.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.EndpointEnabledCondition;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines a management endpoint for a given ID.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@ConfigurationReader(basePrefix = EndpointConfiguration.PREFIX)
@Requires(condition = EndpointEnabledCondition.class)
public @interface Endpoint {

    /**
     * Whether endpoints are enabled by default.
     */
    boolean ENABLED = true;

    /**
     * Whether endpoints are sensitive by default.
     */
    boolean SENSITIVE = true;

    /**
     * The default prefix.
     */
    String DEFAULT_PREFIX = "endpoints";

    /**
     * The ID used to refer to all.
     */
    String ALL = "all";

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

    /**
     * @return The default prefix to use
     */
    @AliasFor(annotation = ConfigurationReader.class, member = ConfigurationReader.BASE_PREFIX)
    String prefix() default DEFAULT_PREFIX;

    /**
     * @return If the endpoint is enabled when no configuration is provided
     */
    boolean defaultEnabled() default ENABLED;

    /**
     * @return If the endpoint is sensitive when no configuration is provided
     */
    boolean defaultSensitive() default SENSITIVE;

    /**
     * @return The configuration key to look for when no configuration is provided
     */
    String defaultConfigurationId() default ALL;
}

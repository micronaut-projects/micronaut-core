/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.reflection;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Applies the {@value ReflectionIntrospectionPolicy#PROPERTY_ALLOW_REFLECTION} property of the application
 * configuration to the {@link ReflectionIntrospectionPolicy} when the context starts, and takes it back when
 * the context closes, leaving the patterns of the contexts that are still running.
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Context
@Internal
@Experimental
@ConfigurationProperties(ReflectionIntrospectionConfiguration.PREFIX)
public final class ReflectionIntrospectionConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "micronaut.introspection";

    private List<String> allowReflection = List.of();
    // the contribution of this context only, so closing this context leaves the patterns the contexts still
    // running contributed
    private ReflectionIntrospectionPolicy.@Nullable Registration registration;

    /**
     * Creates the configuration; it is instantiated by the context.
     */
    public ReflectionIntrospectionConfiguration() {
    }

    /**
     * The patterns of the types the shared introspector may describe reflectively.
     *
     * @return The patterns
     */
    public List<String> getAllowReflection() {
        return allowReflection;
    }

    /**
     * The patterns of the types the shared {@link io.micronaut.core.beans.BeanIntrospector} may describe
     * reflectively when it has no generated introspection for them. A pattern is a class name where {@code *}
     * stands for any sequence of characters: {@code com.example.model.*} allows a package and its sub packages.
     * No type is allowed by default.
     *
     * @param allowReflection The patterns
     */
    public void setAllowReflection(List<String> allowReflection) {
        this.allowReflection = allowReflection == null ? List.of() : allowReflection;
    }

    @PostConstruct
    void apply() {
        registration = ReflectionIntrospectionPolicy.configure(allowReflection);
    }

    @PreDestroy
    void withdraw() {
        ReflectionIntrospectionPolicy.@Nullable Registration contributed = registration;
        if (contributed != null) {
            registration = null;
            contributed.close();
        }
    }
}

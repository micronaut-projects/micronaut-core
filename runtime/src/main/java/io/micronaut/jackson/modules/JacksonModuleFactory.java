/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.jackson.modules;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jackson.JacksonConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Contains factories for modules.
 *
 * @author graemerocher
 * @since 1.1
 */
@Factory
public class JacksonModuleFactory {

    /**
     * Provides the Java Time module.
     *
     * @return The java time module
     */
    @Named("javaTimeModule")
    @Singleton
    @Requires(classes = JavaTimeModule.class)
    @Requires(notEnv = Environment.ANDROID)
    @Requires(property = JacksonConfiguration.PROPERTY_MODULE_SCAN, value = StringUtils.FALSE)
    @Indexed(Module.class)
    protected Module javaTimeModule() {
        return new JavaTimeModule();
    }

    /**
     * Provides the JDK 8 module.
     *
     * @return The JDK 8 module
     */
    @Named("jdk8Module")
    @Singleton
    @Requires(classes = Jdk8Module.class)
    @Requires(property = JacksonConfiguration.PROPERTY_MODULE_SCAN, value = StringUtils.FALSE)
    @Indexed(Module.class)
    protected Module jdk8Module() {
        return new Jdk8Module();
    }
}

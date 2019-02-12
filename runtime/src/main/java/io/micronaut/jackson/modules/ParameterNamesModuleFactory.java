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
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jackson.JacksonConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Provides the parameter names module if present.
 *
 * @author graemerocher
 * @since 1.1
 */
@Requires(classes = ParameterNamesModule.class)
@Requires(property = JacksonConfiguration.PROPERTY_MODULE_SCAN, value = StringUtils.FALSE)
public class ParameterNamesModuleFactory {

    /**
     * Provides the parameter names module if present.
     *
     * @return The parameter names module
     */
    @Named("parameterNamesModule")
    @Singleton
    @Requires(classes = ParameterNamesModule.class)
    @Requires(property = JacksonConfiguration.PROPERTY_MODULE_SCAN, value = StringUtils.FALSE)
    @Indexed(Module.class)
    protected Module parameterNamesModule() {
        return new ParameterNamesModule();
    }
}

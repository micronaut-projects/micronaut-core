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

package io.micronaut.configuration.ribbon.discovery;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import io.micronaut.configuration.ribbon.AbstractRibbonClientConfig;
import io.micronaut.context.env.Environment;

/**
 * Standard config that is used if a custom one is not supplied.
 *
 * @author graemerocher
 * @since 1.0
 */
class StandardNameClientConfig extends AbstractRibbonClientConfig {

    private final String name;
    private final IClientConfig defaultConfig;

    /**
     * Default constructor.
     *
     * @param environment The environment
     * @param name The name
     * @param defaultConfig The default config
     */
    StandardNameClientConfig(Environment environment, String name, IClientConfig defaultConfig) {
        super(environment);
        this.name = name;
        this.defaultConfig = defaultConfig;
    }

    @Override
    protected <T> T get(IClientConfigKey<T> key, Class<T> type, T defaultValue) {
        return super.get(key, type, defaultConfig.get(key, defaultValue));
    }

    @Override
    public String getClientName() {
        return name;
    }

    @Override
    public String getNameSpace() {
        return name + '.' + PREFIX;
    }

}

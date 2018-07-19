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

package io.micronaut.configuration.ribbon;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.env.Environment;

/**
 * This bean creates a named {@link com.netflix.client.config.IClientConfig} for each property under the prefix {@link #PREFIX}.
 *
 * @author graemerocher
 * @since 1.0
 */
@EachProperty(NamedRibbonClientConfig.PREFIX)
public class NamedRibbonClientConfig extends AbstractRibbonClientConfig {
    public static final String PREFIX = "ribbon.clients";
    private final String name;

    /**
     * Constructor.
     * @param name name from configuration
     * @param environment environment
     */
    public NamedRibbonClientConfig(@Parameter String name, Environment environment) {
        super(environment);
        this.name = name;
    }

    @Override
    public String getClientName() {
        return name;
    }

    @Override
    public String getNameSpace() {
        return PREFIX + '.' + name;
    }
}

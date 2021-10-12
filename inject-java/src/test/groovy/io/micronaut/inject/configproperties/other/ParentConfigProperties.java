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
package io.micronaut.inject.configproperties.other;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties("parent")
public class ParentConfigProperties {

    private String name;
    protected String nationality;

    @ConfigurationBuilder(configurationPrefix = "engine", prefixes = "with")
    protected Engine.Builder builder = Engine.builder();

    public Engine.Builder getBuilder() {
        return builder;
    }

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }

}

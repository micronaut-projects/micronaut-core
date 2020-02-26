/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;

@Factory
public class Neo4jPropertiesFactory {

    @Singleton
    @Replaces(Neo4jProperties.class)
    @Requires(property = "spec.name", value = "ConfigurationPropertiesFactorySpec")
    Neo4jProperties neo4jProperties() {
        Neo4jProperties props = new Neo4jProperties();
        try {
            props.uri = new URI("https://google.com");
        } catch (URISyntaxException e) {
        }
        return props;
    }
}

/*
 * Copyright 2018 original authors
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
package org.particleframework.configuration.neo4j.bolt;

import org.neo4j.driver.v1.Driver;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.runtime.context.scope.Refreshable;


/**
 * Default factory for creating the Neo4j {@link Driver}
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class Neo4jDriverFactory {

    private final Neo4jDriverBuilder driverBuilder;

    public Neo4jDriverFactory(Neo4jDriverBuilder driverBuilder) {
        this.driverBuilder = driverBuilder;
    }

    @Bean(preDestroy = "close")
    @Refreshable(Neo4jBoltConfiguration.PREFIX)
    public Driver boltDriver() {
        return driverBuilder.buildDriver();
    }
}

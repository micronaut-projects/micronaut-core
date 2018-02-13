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
package org.particleframework.configuration.mongo.reactive;

import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.EachProperty;

/**
 * Creates a named configuration for each entry under {@link MongoSettings#MONGODB_SERVERS}
 *
 * @author graemerocher
 * @since 1.0
 */
@EachProperty(value = MongoSettings.MONGODB_SERVERS)
public class NamedMongoConfiguration extends MongoConfiguration {

    private final String serverName;

    public NamedMongoConfiguration(@Argument String serverName) {
        this.serverName = serverName;
    }

    public String getServerName() {
        return serverName;
    }
}

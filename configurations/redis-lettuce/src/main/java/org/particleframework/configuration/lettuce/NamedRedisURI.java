/*
 * Copyright 2017 original authors
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
package org.particleframework.configuration.lettuce;

import io.lettuce.core.RedisURI;

/**
 * A named {@link RedisURI} configuration used to configure multiple Redis server
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NamedRedisURI extends RedisURI {
    private final String serverName;

    public NamedRedisURI(String serverName) {
        setHost("localhost"); // default to localhost
        this.serverName = serverName;
    }

    /**
     * @return The name of the server
     */
    public String getServerName() {
        return serverName;
    }
}

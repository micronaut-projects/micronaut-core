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
import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.annotation.Requires;

/**
 * In the case where the <tt>particle.redis.uri</tt> is not specified use the default configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("particle.redis")
@Primary
@Requires(missingProperty = "particle.redis.uri")
public class DefaultRedisConfiguration extends RedisURI {
    public DefaultRedisConfiguration() {
        setHost("localhost"); // localhost by default
    }
}

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

import io.lettuce.core.RedisClient;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.ForEach;


/**
 * A factory bean for constructing {@link RedisClient} instances from {@link NamedRedisURI} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class NamedRedisClientFactory {

    @Bean(preDestroy = "shutdown")
    @ForEach(NamedRedisURI.class)
    public RedisClient redisClient(NamedRedisURI redisURI) {
        return RedisClient.create(redisURI);
    }
}

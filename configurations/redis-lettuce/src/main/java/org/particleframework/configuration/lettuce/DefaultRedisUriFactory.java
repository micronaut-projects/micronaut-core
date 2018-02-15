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
import org.particleframework.context.annotation.*;

import javax.inject.Singleton;

/**
 * In the case where the <tt>particle.redis.uri</tt> is specified use this factory bean
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
@Requires(property = "particle.redis.uri")
public class DefaultRedisUriFactory {

    @Bean
    @Primary
    @Singleton
    RedisURI redisURI(@Value("${particle.redis.uri}") String uri) {
        return RedisURI.create(uri);
    }
}

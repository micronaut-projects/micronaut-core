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

package io.micronaut.configuration.lettuce;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

/**
 * In the case where the <tt>redis.uri</tt> is not specified use the default configuration.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(RedisSetting.PREFIX)
@Primary
@Requires(property = RedisSetting.PREFIX)
public class DefaultRedisConfiguration extends AbstractRedisConfiguration {
}

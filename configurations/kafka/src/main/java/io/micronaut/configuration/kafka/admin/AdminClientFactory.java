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

package io.micronaut.configuration.kafka.admin;

import io.micronaut.configuration.kafka.config.KafkaDefaultConfiguration;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import org.apache.kafka.clients.admin.AdminClient;

import javax.inject.Singleton;

/**
 * Creates the Kakfa {@link org.apache.kafka.clients.admin.AdminClient}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
@Requires(beans = KafkaDefaultConfiguration.class)
public class AdminClientFactory {

    /**
     * Creates the admin client.
     *
     * @param configuration The configuration to use.
     * @return The admin client
     */
    @Bean(preDestroy = "close")
    @Singleton
    AdminClient adminClient(KafkaDefaultConfiguration configuration) {
        return AdminClient.create(configuration.getConfig());
    }

}

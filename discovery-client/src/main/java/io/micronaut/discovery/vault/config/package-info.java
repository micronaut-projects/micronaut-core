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
/**
 *  Classes related to using HashiCorp Vault as a distributed configuration client.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
@Requires(property = ConfigurationClient.ENABLED, value = "true", defaultValue = "false")
@Requires(property = VaultClientConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX + ".enabled", value = "true")
@Configuration
package io.micronaut.discovery.vault.config;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.config.ConfigurationClient;

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

/**
 * Flyway integration with Micronaut.
 *
 * @author Iván López
 * @see <a href="https://flywaydb.org/">Flyway</a>
 * @since 1.1
 */
@Configuration
@Requires(property = "flyway.enabled", notEquals = "false")
package io.micronaut.configuration.dbmigration.flyway;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;

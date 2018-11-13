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
 * Liquibase integration with Micronaut.
 *
 * @author Sergio del Amo
 * @see <a href="http://www.liquibase.org">Liquibase</a>
 * @since 1.1
 */
@Configuration
@Requires(property = "liquibase.enabled", notEquals = "false")
@Requires(classes = Liquibase.class)
package io.micronaut.configuration.dbmigration.liquibase;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import liquibase.Liquibase;

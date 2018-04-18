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
 * Configuration for Apache DBCP data sources.
 */
@Configuration
@Requires(classes = BasicDataSource.class)
package io.micronaut.configuration.jdbc.dbcp;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import org.apache.commons.dbcp2.BasicDataSource;

/*
 * Copyright 2018 original authors
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
 * This configuration contains setup class for GORM for Neo4j
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = Driver.class)
@Requires(entities = Entity.class)
package io.micronaut.configuration.neo4j.gorm;

import grails.gorm.annotation.Entity;
import io.micronaut.context.annotation.Requires;
import org.neo4j.driver.v1.Driver;
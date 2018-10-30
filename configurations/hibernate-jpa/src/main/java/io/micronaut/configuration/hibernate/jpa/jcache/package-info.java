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
 * This package contains a configuration for Hibernate JCache.
 *
 * @author Marcel Overdijk
 * @since 1.0.1
 */
@Configuration
@Requires(classes = {CacheManager.class, ConfigSettings.class})
package io.micronaut.configuration.hibernate.jpa.jcache;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import org.hibernate.cache.jcache.ConfigSettings;

import javax.cache.CacheManager;

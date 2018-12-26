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

package io.micronaut.discovery.spring.config.client;

import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.spring.SpringCloudConfiguration;
import io.micronaut.http.client.annotation.Client;

/**
 * Abstract implementation of {@link SpringCloudConfigClient}.
 *
 * @author Thiago Locatelli
 * @since 1.0
 */
@SuppressWarnings("unused")
@Client(value = SpringCloudConfiguration.SPRING_CLOUD_CONFIG_ENDPOINT, configuration = SpringCloudConfiguration.class)
@Requires(beans = SpringCloudConfiguration.class)
public abstract class AbstractSpringCloudConfigClient implements SpringCloudConfigClient {
}

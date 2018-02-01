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
package org.particleframework.discovery.eureka.client.v2;

import org.particleframework.discovery.eureka.EurekaConfiguration;
import org.particleframework.http.client.Client;
import org.particleframework.jackson.annotation.JacksonFeatures;
import org.particleframework.validation.Validated;

import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.fasterxml.jackson.databind.SerializationFeature.*;

/**
 * Compile time implementation of {@link EurekaClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Client(id = EurekaClient.SERVICE_ID, path = "/eureka", configuration = EurekaConfiguration.class)
@JacksonFeatures(
    enabledSerializationFeatures = WRAP_ROOT_VALUE,
    disabledSerializationFeatures = WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED,
    enabledDeserializationFeatures = {UNWRAP_ROOT_VALUE, ACCEPT_SINGLE_VALUE_AS_ARRAY}
)
@Validated
public abstract class AbstractEurekaClient implements EurekaClient {
}

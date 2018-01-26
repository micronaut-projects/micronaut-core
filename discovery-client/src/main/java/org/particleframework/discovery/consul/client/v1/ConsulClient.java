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
package org.particleframework.discovery.consul.client.v1;

import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.annotation.Body;
import org.particleframework.http.annotation.Get;
import org.particleframework.http.annotation.Put;
import org.reactivestreams.Publisher;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * A non-blocking HTTP client for consul
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConsulClient extends ConsulOperations, DiscoveryClient {
    /**
     * The default ID of the consul service
     */
    String SERVICE_ID = "consul";
}

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
package io.micronaut.management.endpoint.info;

import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.annotation.Read;
import io.reactivex.Single;

/**
 * <p>Exposes an {@link Endpoint} to provide information about the application.</p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
@Endpoint(InfoEndpoint.NAME)
public class InfoEndpoint {

    /**
     * Constant with the name of the Info endpoint.
     */
    public static final String NAME = "info";

    /**
     * The prefix for Info endpoint configuration.
     */
    public static final String PREFIX = EndpointConfiguration.PREFIX + "." + NAME;

    private InfoAggregator infoAggregator;
    private InfoSource[] infoSources;

    /**
     * @param infoAggregator The {@link InfoAggregator}
     * @param infoSources    The {@link InfoSource}
     */
    public InfoEndpoint(InfoAggregator infoAggregator, InfoSource[] infoSources) {
        this.infoAggregator = infoAggregator;
        this.infoSources = infoSources;
    }

    /**
     * Returns the info response.
     *
     * @return A {@link org.reactivestreams.Publisher} of the info response
     */
    @Read
    Single getInfo() {
        return Single.fromPublisher(infoAggregator.aggregate(infoSources));
    }
}

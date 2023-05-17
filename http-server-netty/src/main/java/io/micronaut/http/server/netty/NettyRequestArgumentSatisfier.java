/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.binders.NettyServerRequestBinderRegistry;
import io.micronaut.web.router.RouteMatch;
import jakarta.inject.Singleton;

/**
 * A class containing methods to aid in satisfying arguments of a {@link io.micronaut.web.router.Route}.
 * Contains Netty specific extensions - setting the body required for blocking binders.
 *
 * @author Graeme Rocher
 * @author Vladimir Orany
 * @since 1.0
 */
@Primary
@Singleton
@Internal
final class NettyRequestArgumentSatisfier extends RequestArgumentSatisfier {

    /**
     * Constructor.
     *
     * @param requestBinderRegistry The request argument binder
     */
    NettyRequestArgumentSatisfier(NettyServerRequestBinderRegistry requestBinderRegistry) {
        super(requestBinderRegistry);
    }

    @Override
    public void fulfillArgumentRequirementsBeforeFilters(RouteMatch<?> route, HttpRequest<?> request) {
        super.fulfillArgumentRequirementsBeforeFilters(route, request);
    }

}

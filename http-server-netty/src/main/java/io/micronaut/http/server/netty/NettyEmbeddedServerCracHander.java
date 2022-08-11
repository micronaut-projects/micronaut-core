/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.crac.support.CracCondition;
import io.micronaut.crac.support.CracContext;
import io.micronaut.crac.support.OrderedCracResource;

/**
 * Register the NettyEmbedded server as a CRaC resource on startup if CRaC is enabled.
 *
 * @author Tim Yates
 * @since 3.7.0
 */
@Experimental
@EachBean(NettyEmbeddedServer.class)
@Requires(condition = CracCondition.class)
public class NettyEmbeddedServerCracHander implements OrderedCracResource {

    private final NettyEmbeddedServer server;

    public NettyEmbeddedServerCracHander(NettyEmbeddedServer server) {
        this.server = server;
    }

    @Override
    public void beforeCheckpoint(CracContext context) throws Exception {
        server.stop();
    }

    @Override
    public void afterRestore(CracContext context) throws Exception {
        server.start();
    }
}

/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev.livereload.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.livereload.LiveReloadServerFactory;

import java.io.IOException;

/**
 * The factory the launcher service-loads.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class NettyLiveReloadServerFactory implements LiveReloadServerFactory {

    @Override
    public LiveReloadServer start(int port) throws IOException {
        return NettyLiveReloadServer.start(port);
    }
}

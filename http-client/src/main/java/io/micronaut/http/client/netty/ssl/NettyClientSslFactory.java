/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.client.netty.ssl;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.netty.NettySslContextBuilder;
import jakarta.inject.Singleton;

@Singleton
@BootstrapContextCompatible
public class NettyClientSslFactory {
    @Experimental
    public @NonNull NettySslContextBuilder builder(@NonNull HttpClientConfiguration configuration) {
        return builder();
    }

    public @NonNull NettySslContextBuilder builder() {
        return new NettySslContextBuilder(false);
    }
}

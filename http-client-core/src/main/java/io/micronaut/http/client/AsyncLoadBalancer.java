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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.discovery.ServiceInstance;

import java.util.concurrent.CompletionStage;

/**
 * The {@link CompletionStage} counterpart of a {@link LoadBalancer}. A {@link LoadBalancer} that
 * can select an instance without a publisher also implements this interface, and the HTTP clients
 * then call {@link #selectAsync(Object)} instead of {@link LoadBalancer#select(Object)}.
 *
 * <p>An implementation returns a new stage for each call, which a caller may cancel. The
 * framework never cancels a stage it did not create: it ignores its result instead.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncLoadBalancer {

    /**
     * Selects the next available server. The stage completes with {@code null} when no instance
     * is available, which the clients handle as they handle an empty publisher of
     * {@link LoadBalancer#select(Object)}.
     *
     * @param discriminator An object used to discriminate the server to select. Usually the service ID
     * @return A stage completed with the selected {@link ServiceInstance}, with {@code null} for no instance, or with the error of the selection
     */
    CompletionStage<@Nullable ServiceInstance> selectAsync(@Nullable Object discriminator);
}

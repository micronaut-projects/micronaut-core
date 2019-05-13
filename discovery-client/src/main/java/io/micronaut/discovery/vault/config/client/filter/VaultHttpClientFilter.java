/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.discovery.vault.config.client.filter;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.discovery.vault.VaultClientConstants;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import org.reactivestreams.Publisher;

/**
 *  Vault Http Filter used to set auth token header.
 *
 *  @author thiagolocatelli
 *  @author graemerocher
 *  @since 1.1.1
 */
@Filter("/v1/**")
@Requires(property = VaultClientConstants.PREFIX + ".token")
@BootstrapContextCompatible
public class VaultHttpClientFilter implements HttpClientFilter {

    private String vaultToken;

    /**
     * @param vaultToken the vault token
     */
    public VaultHttpClientFilter(@Value("${vault.client.token}") final String vaultToken) {
        this.vaultToken = vaultToken;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(
            final MutableHttpRequest<?> request,
            final ClientFilterChain chain) {

        return chain.proceed(request.header(VaultClientConstants.X_VAULT_TOKEN_HEADER, vaultToken));
    }
}

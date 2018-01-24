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
package org.particleframework.http.client;

import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.runtime.server.EmbeddedServer;

import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Abstraction over {@link ServerSelector} lookup
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class DefaultServerSelectorResolver implements ServerSelectorResolver {

    private final Optional<EmbeddedServer> embeddedServer;
    private final Map<String, ServerSelectorProvider> serverSelectorProviderMap;

    public DefaultServerSelectorResolver(Optional<EmbeddedServer> embeddedServer, ServerSelectorProvider...providers) {
        this.embeddedServer = embeddedServer;
        if(ArrayUtils.isNotEmpty(providers)) {
            this.serverSelectorProviderMap = new HashMap<>(providers.length);
            for (ServerSelectorProvider provider : providers) {
                serverSelectorProviderMap.put(provider.getId(), provider);
            }
        }
        else {
            this.serverSelectorProviderMap = Collections.emptyMap();
        }
    }

    @Override
    public Optional<ServerSelector> resolve(String... serviceReferences) {
        if(ArrayUtils.isEmpty(serviceReferences) || StringUtils.isEmpty(serviceReferences[0])) {
            return Optional.empty();
        }
        String reference = serviceReferences[0];

        if(serverSelectorProviderMap.containsKey(reference)) {
            return Optional.ofNullable(serverSelectorProviderMap.get(reference).getSelector());
        }
        else if(reference.startsWith("/")) {
            // current server reference
            if(embeddedServer.isPresent()) {
                URL url = embeddedServer.get().getURL();
                return Optional.of(discriminator -> url);
            }
            else {
                return Optional.empty();
            }
        }
        else if(reference.indexOf('/') > -1) {
            try {
                URL url = new URL(reference);
                return Optional.of(discriminator -> url);
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}

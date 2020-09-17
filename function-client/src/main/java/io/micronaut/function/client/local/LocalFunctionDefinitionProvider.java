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
package io.micronaut.function.client.local;

import io.micronaut.context.annotation.Requires;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.function.client.FunctionDefinitionProvider;
import io.micronaut.runtime.server.EmbeddedServer;

import javax.inject.Singleton;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author graemerocher
 * @since 1.0
 */
@Requires(beans = {EmbeddedServer.class, LocalFunctionRegistry.class})
@Singleton
public class LocalFunctionDefinitionProvider implements FunctionDefinitionProvider {

    private final EmbeddedServer embeddedServer;
    private final LocalFunctionRegistry localFunctionRegistry;

    /**
     * Constructor.
     * @param embeddedServer embeddedServer
     * @param localFunctionRegistry localFunctionRegistry
     */
    public LocalFunctionDefinitionProvider(EmbeddedServer embeddedServer, LocalFunctionRegistry localFunctionRegistry) {
        this.embeddedServer = embeddedServer;
        this.localFunctionRegistry = localFunctionRegistry;
    }

    @Override
    public Collection<FunctionDefinition> getFunctionDefinitions() {
        if (!embeddedServer.isRunning()) {
            return Collections.emptyList();
        }

        Map<String, URI> availableFunctions = localFunctionRegistry.getAvailableFunctions();
        return availableFunctions.entrySet().stream().map((Function<Map.Entry<String, URI>, FunctionDefinition>) stringURIEntry -> new FunctionDefinition() {
            @Override
            public String getName() {
                return stringURIEntry.getKey();
            }

            @Override
            public Optional<URI> getURI() {
                return Optional.of(embeddedServer.getURI().resolve(stringURIEntry.getValue()));
            }
        }).collect(Collectors.toList());
    }
}

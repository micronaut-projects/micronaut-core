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
package io.micronaut.crac.support;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A singleton to return the Global context for Coordinated Restore at Checkpoint.
 *
 * @author Tim Yates
 * @since 3.7.0
 */
@Experimental
@Factory
public class GlobalCracContextFactory {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalCracContextFactory.class);

    /**
     * Creates a delegating context class to allow CRaC to be used in Micronaut.
     *
     * @param cracContextProvider API to get the Context for checkpoint/restore notifications.
     * @return The Global context for Coordinated Restore at Checkpoint.
     */
    @NonNull
    @Singleton
    public CracContext createContext(@NonNull CracContextProvider cracContextProvider) {
        CracContext cracContext = new DefaultCracContext(cracContextProvider.provideContext());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating a Global CRaC context delegate {}", cracContext);
        }
        return cracContext;
    }
}

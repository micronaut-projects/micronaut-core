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
package io.micronaut.runtime;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextLifeCycle;
import io.micronaut.context.env.Environment;

/**
 * <p>An {@link EmbeddedApplication} is a runnable application that starts and manages the {@link ApplicationContext}.</p>
 *
 * <p>This interface is the parent interface of {@link io.micronaut.runtime.server.EmbeddedServer} which formalizes the concept of a
 * server that exposes a port and address</p>
 *
 * <p>Applications that did not expose a port and address (messaging applications, CLI applications etc.
 * may wish to use this interface instead</p>
 *
 * @param <T>
 * @see io.micronaut.runtime.server.EmbeddedServer
 * @author Graeme Rocher
 * @since 1.0
 */
public interface EmbeddedApplication<T extends EmbeddedApplication> extends ApplicationContextLifeCycle<T> {


    /**
     * @return The {@link ApplicationContext} for the server
     */
    @Override
    ApplicationContext getApplicationContext();

    /**
     * @return The {@link ApplicationConfiguration} instance
     */
    ApplicationConfiguration getApplicationConfiguration();

    /**
     * @return The {@link Environment} for the server
     */
    default Environment getEnvironment() {
        return getApplicationContext().getEnvironment();
    }

    /**
     * This method returns whether the application is a server application or not. Server applications
     * require that the thread be kept a live, preventing the application from exiting.
     *
     * @return True if the application is a server
     */
    default boolean isServer() {
        return false;
    }

    /**
     * Most servers shutdown cleanly, however some integrations make require a forced exist. If
     * true is returned Micronaut will explicitly invoke System.exit(..).
     *
     * @return Whether exiting the server should be forced.
     */
    default boolean isForceExit() {
        return false;
    }
}

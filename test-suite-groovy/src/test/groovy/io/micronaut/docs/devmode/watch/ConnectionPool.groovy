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
package io.micronaut.docs.devmode.watch

/**
 * A stand-in for a connection pool such as Hikari's: its URL is fixed when it is created, its credentials can change.
 */
final class ConnectionPool {

    final String url
    volatile String username
    volatile String password
    volatile int evictions

    ConnectionPool(PoolConfiguration configuration) {
        url = configuration.url
        setCredentials(configuration.username, configuration.password)
    }

    void setCredentials(String username, String password) {
        this.username = username
        this.password = password
    }

    /**
     * Closes the idle connections and the busy ones once they are returned, so that new ones use the new credentials.
     */
    void softEvictConnections() {
        evictions++
    }
}

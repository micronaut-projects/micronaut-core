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
package io.micronaut.docs.devmode.watch;

/**
 * A stand-in for a connection pool such as Hikari's: its URL is fixed when it is created, its credentials can change.
 */
public final class ConnectionPool {

    private final String url;
    private volatile String username;
    private volatile String password;
    private volatile int evictions;

    public ConnectionPool(PoolConfiguration configuration) {
        this.url = configuration.getUrl();
        setCredentials(configuration.getUsername(), configuration.getPassword());
    }

    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Closes the idle connections and the busy ones once they are returned, so that new ones use the new credentials.
     */
    public void softEvictConnections() {
        evictions++;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getEvictions() {
        return evictions;
    }
}

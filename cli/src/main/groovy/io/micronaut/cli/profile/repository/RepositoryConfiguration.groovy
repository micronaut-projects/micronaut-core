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
package io.micronaut.cli.profile.repository

import org.springframework.util.ObjectUtils

/**
 *  The configuration of a repository. See {@link org.springframework.boot.cli.compiler.grape.RepositoryConfiguration}
 *  Created to support configuration with authentication
 *
 * @author James Kleeh
 * @since 1.0
 */
class RepositoryConfiguration {

    final String name
    final URI uri
    final boolean snapshotsEnabled
    final String username
    final String password

    /**
     * Creates a new {@code RepositoryConfiguration} instance.
     *
     * @param name The name of the repository
     * @param uri The uri of the repository
     * @param snapshotsEnabled {@code true} if the repository should enable access to snapshots, {@code false} otherwise
     */
    public RepositoryConfiguration(String name, URI uri, boolean snapshotsEnabled) {
        this.name = name
        this.uri = uri
        this.snapshotsEnabled = snapshotsEnabled
    }

    /**
     * Creates a new {@code RepositoryConfiguration} instance.
     *
     * @param name The name of the repository
     * @param uri The uri of the repository
     * @param snapshotsEnabled {@code true} if the repository should enable access to snapshots, {@code false} otherwise
     * @param username The username needed to authenticate with the repository
     * @param password The password needed to authenticate with the repository
     */
    public RepositoryConfiguration(String name, URI uri, boolean snapshotsEnabled, String username, String password) {
        this.name = name
        this.uri = uri
        this.snapshotsEnabled = snapshotsEnabled
        this.username = username
        this.password = password
    }

    @Override
    String toString() {
        "RepositoryConfiguration [name=$name, uri=$uri, snapshotsEnabled=$snapshotsEnabled]"
    }

    @Override
    int hashCode() {
        ObjectUtils.nullSafeHashCode(name)
    }

    boolean hasCredentials() {
        username && password
    }

    @Override
    boolean equals(Object obj) {
        if (obj == null) {
            return false
        }
        if (this.is(obj)) {
            return true
        }
        String name = null
        if (obj instanceof org.springframework.boot.cli.compiler.grape.RepositoryConfiguration) {
            name = obj.name
        } else if (obj instanceof RepositoryConfiguration) {
            name = obj.name
        }
        this.name == name
    }
}

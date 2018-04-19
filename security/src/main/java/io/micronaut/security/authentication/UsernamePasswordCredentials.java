/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.security.authentication;

import java.io.Serializable;
import java.util.Objects;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class UsernamePasswordCredentials implements Serializable, AuthenticationRequest {
    private String username;
    private String password;

    /**
     * Empty constructor.
     */
    public UsernamePasswordCredentials() { }

    /**
     *
     * @param username e.g. admin
     * @param password raw password
     */
    public UsernamePasswordCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * username getter.
     * @return e.g. admin
     */
    public String getUsername() {
        return username;
    }

    /**
     * username setter.
     * @param username e.g. admin
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * password getter.
     * @return raw password
     */
    public String getPassword() {
        return password;
    }

    /**
     * password setter.
     * @param password raw password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UsernamePasswordCredentials that = (UsernamePasswordCredentials) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }
}

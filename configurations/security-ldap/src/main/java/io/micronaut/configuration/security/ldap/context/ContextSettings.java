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

package io.micronaut.configuration.security.ldap.context;

/**
 * Contract to hold settings for creating an LDAP context.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface ContextSettings {

    /**
     * @return True if the context should be pooled
     */
    boolean getPooled();

    /**
     * @return The factory class
     */
    String getFactory();

    /**
     * @return The URL of the LDAP server
     */
    String getUrl();

    /**
     * @return The user DN to bind with
     */
    String getDn();

    /**
     * @return The password to bind with
     */
    String getPassword();
}

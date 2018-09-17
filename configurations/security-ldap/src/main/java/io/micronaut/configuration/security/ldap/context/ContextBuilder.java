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

import javax.annotation.Nullable;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

/**
 * Contract for building and closing LDAP contexts.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface ContextBuilder {

    /**
     * @param contextSettings The settings to use to build the context
     * @return The context
     * @throws NamingException If an error occurs
     */
    DirContext build(ContextSettings contextSettings) throws NamingException;

    /**
     * @param factory  The factory class
     * @param server   The ldap server
     * @param user     The user DN to bind
     * @param password The password to bind
     * @param pooled   If the query should be pooled
     * @return The context
     * @throws NamingException If an error occurs
     */
    DirContext build(String factory, String server, String user, String password, boolean pooled) throws NamingException;

    /**
     * Closes the given context.
     *
     * @param context The context to close
     */
    void close(@Nullable DirContext context);
}

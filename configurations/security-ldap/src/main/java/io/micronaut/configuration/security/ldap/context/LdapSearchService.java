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

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import java.util.List;
import java.util.Optional;

/**
 * Contract for searching LDAP using an existing context.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface LdapSearchService {

    /**
     * Searches LDAP with the given settings and returns an optional result.
     *
     * @param managerContext The context to search with
     * @param settings       The settings for searching
     * @return An optional search result
     * @throws NamingException If the search fails
     */
    Optional<LdapSearchResult> searchFirst(DirContext managerContext, SearchSettings settings) throws NamingException;

    /**
     * Searches LDAP with th e given settings and returns a list of results.
     *
     * @param managerContext The context to search with
     * @param settings       The settings for searching
     * @return A list of results, empty if none found
     * @throws NamingException If the search fails
     */
    List<LdapSearchResult> search(DirContext managerContext, SearchSettings settings) throws NamingException;
}

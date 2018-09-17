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

package io.micronaut.configuration.security.ldap.group;

import io.micronaut.configuration.security.ldap.context.LdapSearchResult;
import io.micronaut.configuration.security.ldap.context.SearchProvider;

import javax.naming.NamingException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Contract to allow the list of groups returned from LDAP to be transformed
 * and appended to from other sources.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface LdapGroupProcessor {

    /**
     * Processes groups returned from LDAP.
     *
     * @param attribute    The group attribute in the context
     * @param result       The search result of the user
     * @param groupResults The provider responsible for querying LDAP
     * @return The groups to populate in the authentication
     * @throws NamingException If the search provider fails
     */
    Set<String> process(String attribute, LdapSearchResult result, SearchProvider groupResults) throws NamingException;

    /**
     * Provides a way to add additional groups to the ldap group search.
     *
     * @param result The ldap search result
     * @return Any additional groups that should be added to the authentication
     */
    default Set<String> getAdditionalGroups(LdapSearchResult result) {
        return Collections.emptySet();
    }

    /**
     * Transform the group into the format required.
     *
     * @param group The group to process
     * @return An optional modified group
     */
    default Optional<String> processGroup(String group) {
        return Optional.of(group);
    }
}

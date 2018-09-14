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

import javax.inject.Singleton;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.*;

/**
 * Default implementation of {@link LdapSearchService}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class DefaultLdapSearchService implements LdapSearchService {

    @Override
    public Optional<LdapSearchResult> searchFirst(DirContext managerContext, SearchSettings settings) throws NamingException {
        List<LdapSearchResult> results = search(managerContext, settings);
        if (results.size() > 0) {
            LdapSearchResult result = results.get(0);
            return Optional.of(result);
        }
        return Optional.empty();
    }

    @Override
    public List<LdapSearchResult> search(DirContext managerContext, SearchSettings settings) throws NamingException {
        SearchControls ctrls = new SearchControls();
        ctrls.setReturningAttributes(settings.getAttributes());
        if (settings.isSubtree()) {
            ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        }
        NamingEnumeration<SearchResult> results = managerContext.search(settings.getBase(), settings.getFilter(), settings.getArguments(), ctrls);
        return createResults(results);
    }

    /**
     * Builds {@link LdapSearchResult} from the LDAP results.
     *
     * @param results The LDAP results
     * @return The list of {@link LdapSearchResult}
     * @throws NamingException If an error occurs
     */
    protected  List<LdapSearchResult> createResults(NamingEnumeration<SearchResult> results) throws NamingException {
        List<LdapSearchResult> searchResults = new ArrayList<>();
        while (results.hasMore()) {
            SearchResult result = results.next();
            Attributes attributes = result.getAttributes();
            String dn = result.getNameInNamespace();

            searchResults.add(new LdapSearchResult(attributes, dn));
        }
        return searchResults;
    }
}

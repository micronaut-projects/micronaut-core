package io.micronaut.security.ldap.context;

import javax.inject.Singleton;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.*;

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

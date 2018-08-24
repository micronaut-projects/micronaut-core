package io.micronaut.security.ldap.context;

import io.micronaut.security.ldap.AttributesConvertibleValues;
import io.micronaut.security.ldap.LdapConfigurationProperties;

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

    private final LdapConfigurationProperties ldap;
    private final ContextBuilder contextBuilder;

    DefaultLdapSearchService(LdapConfigurationProperties ldap,
                             ContextBuilder contextBuilder) {
        this.ldap = ldap;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public Optional<LdapSearchResult> searchForUser(DirContext managerContext, String username, String password) throws NamingException {
        List<LdapSearchResult> results = search(managerContext, ldap.getSearch().getSearchSettings(new Object[]{username}));
        if (results.size() > 0) {
            LdapSearchResult result = results.get(0);
            DirContext userContext = null;
            try {
                String dn = result.getDn();
                result.setUsername(username);
                userContext = contextBuilder.build(dn, password);
                if (result.getAttributes() == null) {
                    result.setAttributes(userContext.getAttributes(dn));
                }
            } finally {
                contextBuilder.close(userContext);
            }
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public List<LdapSearchResult> searchForGroups(DirContext managerContext, String userDn) throws NamingException {
        return search(managerContext, ldap.getGroup().getSearchSettings(new Object[]{userDn}));
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

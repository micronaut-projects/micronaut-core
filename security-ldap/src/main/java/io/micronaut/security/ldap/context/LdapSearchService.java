package io.micronaut.security.ldap.context;

import io.micronaut.core.convert.value.ConvertibleMultiValuesMap;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.security.ldap.AttributesConvertibleValues;
import io.micronaut.security.ldap.LdapConfigurationProperties;

import javax.inject.Singleton;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.*;

@Singleton
public class LdapSearchService {

    private final LdapConfigurationProperties.SearchProperties searchProperties;
    private final ContextBuilder contextBuilder;

    LdapSearchService(LdapConfigurationProperties.SearchProperties searchProperties,
                      ContextBuilder contextBuilder) {
        this.searchProperties = searchProperties;
        this.contextBuilder = contextBuilder;
    }

    public ConvertibleValues<Object> search(DirContext managerContext, String username, String password) throws NamingException {
        SearchControls ctrls = new SearchControls();
        ctrls.setReturningAttributes(searchProperties.getAttributes());
        if (searchProperties.isSubtree()) {
            ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        }

        NamingEnumeration<SearchResult> results = managerContext.search(searchProperties.getBase(), searchProperties.getFilter(), new Object[]{username}, ctrls);
        if (results.hasMore()) {
            SearchResult result = results.next();
            Attributes attributes = result.getAttributes();
            String userDn = result.getNameInNamespace();
            DirContext userContext = null;
            try {
                userContext = contextBuilder.build(userDn, password);
                if (attributes == null) {
                    attributes = userContext.getAttributes(userDn);
                }
            } finally {
                contextBuilder.close(userContext);
            }

            return new AttributesConvertibleValues(attributes);
        }
        return null;
    }
}

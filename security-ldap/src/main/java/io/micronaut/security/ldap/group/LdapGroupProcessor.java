package io.micronaut.security.ldap.group;

import io.micronaut.security.ldap.context.LdapSearchResult;
import io.micronaut.security.ldap.context.SearchProvider;

import javax.inject.Provider;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LdapGroupProcessor {

    Set<String> process(String attribute, LdapSearchResult result, SearchProvider groupResults) throws NamingException;

    default Set<String> getAdditionalGroups(LdapSearchResult result) {
        return Collections.emptySet();
    }

    default Optional<String> processGroup(String group) {
        return Optional.of(group);
    }
}

package io.micronaut.security.ldap.group;

import io.micronaut.security.ldap.context.LdapSearchResult;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public interface LdapGroupProcessor {

    Set<String> getGroups(DirContext managerContext, LdapSearchResult result) throws NamingException;

    default Set<String> getAdditionalGroups(DirContext managerContext, LdapSearchResult result) {
        return Collections.emptySet();
    }

    default Optional<String> processGroup(String group) {
        return Optional.of(group);
    }
}

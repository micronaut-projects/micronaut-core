package io.micronaut.security.ldap.context;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import java.util.List;
import java.util.Optional;

public interface LdapSearchService {

    Optional<LdapSearchResult> searchForUser(DirContext managerContext, String username, String password) throws NamingException;

    List<LdapSearchResult> searchForGroups(DirContext managerContext, String userDn) throws NamingException;

    List<LdapSearchResult> search(DirContext managerContext, SearchSettings searchSettings) throws NamingException;
}

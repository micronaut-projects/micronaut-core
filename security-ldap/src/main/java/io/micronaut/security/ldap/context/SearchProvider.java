package io.micronaut.security.ldap.context;

import javax.naming.NamingException;
import java.util.List;

@FunctionalInterface
public interface SearchProvider {

    List<LdapSearchResult> get() throws NamingException;
}

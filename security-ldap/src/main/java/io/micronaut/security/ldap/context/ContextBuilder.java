package io.micronaut.security.ldap.context;

import javax.annotation.Nullable;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

public interface ContextBuilder {

    DirContext buildManager() throws NamingException;

    DirContext build(String user, String password) throws NamingException;

    void close(@Nullable DirContext context);
}

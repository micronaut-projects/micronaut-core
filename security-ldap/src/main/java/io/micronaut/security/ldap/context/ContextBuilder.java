package io.micronaut.security.ldap.context;

import io.micronaut.http.ssl.SslConfiguration;

import javax.annotation.Nullable;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

public interface ContextBuilder {

    DirContext build(ContextSettings contextSettings) throws NamingException;

    DirContext build(String factory, String server, String user, String password, boolean pooled, SslConfiguration sslConfiguration) throws NamingException;

    void close(@Nullable DirContext context);
}

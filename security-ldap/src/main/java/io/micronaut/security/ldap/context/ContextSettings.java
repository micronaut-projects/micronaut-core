package io.micronaut.security.ldap.context;

import io.micronaut.http.ssl.SslConfiguration;

public interface ContextSettings {

    boolean getPooled();
    String getFactory();
    String getUrl();
    String getDn();
    String getPassword();
    SslConfiguration getSslConfiguration();
}

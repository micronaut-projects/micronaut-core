package io.micronaut.security.ldap.context;

public interface ContextSettings {

    boolean getPooled();
    String getFactory();
    String getUrl();
    String getDn();
    String getPassword();
}

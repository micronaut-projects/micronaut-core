package io.micronaut.security.ldap.context;

import io.micronaut.security.ldap.configuration.LdapConfiguration;

public class ContextConfigurationContextSettings implements ContextSettings {

    private final LdapConfiguration.ContextConfiguration contextConfiguration;
    private final String dn;
    private final String password;
    private final boolean pooled;

    public ContextConfigurationContextSettings(LdapConfiguration.ContextConfiguration contextConfiguration) {
        this.contextConfiguration = contextConfiguration;
        this.dn = contextConfiguration.getManagerDn();
        this.password = contextConfiguration.getManagerPassword();
        this.pooled = true;
    }

    public ContextConfigurationContextSettings(LdapConfiguration.ContextConfiguration contextConfiguration,
                                               String dn, String password) {
        this.contextConfiguration = contextConfiguration;
        this.dn = dn;
        this.password = password;
        this.pooled = false;
    }

    @Override
    public boolean getPooled() {
        return pooled;
    }

    @Override
    public String getFactory() {
        return contextConfiguration.getFactory();
    }

    @Override
    public String getUrl() {
        return contextConfiguration.getServer();
    }

    @Override
    public String getDn() {
        return dn;
    }

    @Override
    public String getPassword() {
        return password;
    }
}

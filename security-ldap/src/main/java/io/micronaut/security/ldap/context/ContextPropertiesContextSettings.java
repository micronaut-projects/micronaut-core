package io.micronaut.security.ldap.context;

import io.micronaut.security.ldap.LdapConfigurationProperties;

public class ContextPropertiesContextSettings implements ContextSettings {

    private final LdapConfigurationProperties.ContextProperties contextProperties;
    private final String dn;
    private final String password;
    private final boolean pooled;

    public ContextPropertiesContextSettings(LdapConfigurationProperties.ContextProperties contextProperties) {
        this.contextProperties = contextProperties;
        this.dn = contextProperties.getManagerDn();
        this.password = contextProperties.getManagerPassword();
        this.pooled = true;
    }

    public ContextPropertiesContextSettings(LdapConfigurationProperties.ContextProperties contextProperties,
                                            String dn, String password) {
        this.contextProperties = contextProperties;
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
        return contextProperties.getFactory();
    }

    @Override
    public String getUrl() {
        return contextProperties.getServer();
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

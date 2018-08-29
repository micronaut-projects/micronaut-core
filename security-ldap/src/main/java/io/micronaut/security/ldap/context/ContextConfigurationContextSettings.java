package io.micronaut.security.ldap.context;

import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.security.ldap.configuration.LdapConfiguration;

public class ContextConfigurationContextSettings implements ContextSettings {

    private final LdapConfiguration.ContextConfiguration contextConfiguration;
    private final SslConfiguration sslConfiguration;
    private final String dn;
    private final String password;
    private final boolean pooled;

    public ContextConfigurationContextSettings(LdapConfiguration configuration) {
        this.contextConfiguration = configuration.getContext();
        this.sslConfiguration = configuration.getSsl();
        this.dn = contextConfiguration.getManagerDn();
        this.password = contextConfiguration.getManagerPassword();
        this.pooled = true;
    }

    public ContextConfigurationContextSettings(LdapConfiguration configuration,
                                               String dn, String password) {
        this.contextConfiguration = configuration.getContext();
        this.sslConfiguration = configuration.getSsl();
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

    @Override
    public SslConfiguration getSslConfiguration() {
        return sslConfiguration;
    }
}

package io.micronaut.security.ldap.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.Toggleable;
import io.micronaut.security.config.SecurityConfigurationProperties;
import io.micronaut.security.ldap.context.ContextConfigurationContextSettings;
import io.micronaut.security.ldap.context.ContextSettings;
import io.micronaut.security.ldap.context.SearchSettings;

import javax.annotation.Nullable;
import javax.inject.Inject;

@EachProperty(value = LdapConfiguration.PREFIX, primary = "default")
public class LdapConfiguration implements Toggleable {

    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".ldap";

    private boolean enabled = true;
    private ContextConfiguration context;
    private SearchConfiguration search;
    private GroupConfiguration group;
    private SslConfiguration ssl;
    private final String name;

    LdapConfiguration(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The context configuration
     */
    public ContextConfiguration getContext() {
        return context;
    }

    /**
     * Sets the context configuration.
     *
     * @param contextConfiguration The context configuration
     */
    public void setContext(ContextConfiguration contextConfiguration) {
        this.context = contextConfiguration;
    }

    /**
     * @return The searchForUser configuration
     */
    public SearchConfiguration getSearch() {
        return search;
    }

    /**
     * Sets the searchForUser configuration.
     *
     * @param searchConfiguration The searchForUser configuration
     */
    public void setSearch(SearchConfiguration searchConfiguration) {
        this.search = searchConfiguration;
    }


    /**
     * @return The group configuration
     */
    public GroupConfiguration getGroups() {
        return group;
    }

    /**
     * Sets the group configuration.
     *
     * @param groupConfiguration The group configuration
     */
    public void setGroups(GroupConfiguration groupConfiguration) {
        this.group = groupConfiguration;
    }

    /**
     * @return The group configuration
     */
    public SslConfiguration getSsl() {
        return ssl;
    }

    /**
     * Sets the ssl configuration.
     *
     * @param sslConfiguration The ssl configuration
     */
    public void setSsl(SslConfiguration sslConfiguration) {
        this.ssl = sslConfiguration;
    }

    public ContextSettings getSettings(String dn, String password) {
        return new ContextConfigurationContextSettings(this, dn, password);
    }

    public ContextSettings getManagerSettings() {
        return new ContextConfigurationContextSettings(this);
    }

    @ConfigurationProperties("context")
    public static class ContextConfiguration {

        public static final String PREFIX = LdapConfiguration.PREFIX + ".context";

        private String server;
        private String managerDn;
        private String managerPassword;
        private String factory = "com.sun.jndi.ldap.LdapCtxFactory";

        public String getServer() {
            return server;
        }

        public void setServer(String server) {
            this.server = server;
        }

        public String getManagerDn() {
            return managerDn;
        }

        public void setManagerDn(String managerDn) {
            this.managerDn = managerDn;
        }

        public String getManagerPassword() {
            return managerPassword;
        }

        public void setManagerPassword(String managerPassword) {
            this.managerPassword = managerPassword;
        }

        public String getFactory() {
            return factory;
        }

        public void setFactory(String factory) {
            this.factory = factory;
        }
    }

    @ConfigurationProperties("search")
    public static class SearchConfiguration {

        public static final String PREFIX = LdapConfiguration.PREFIX + ".search";

        private boolean subtree = true;
        private String base = "";
        private String filter = "(uid={0})";
        private String[] attributes = null;

        public boolean isSubtree() {
            return subtree;
        }

        public void setSubtree(boolean subtree) {
            this.subtree = subtree;
        }

        public String getBase() {
            return base;
        }

        public void setBase(String base) {
            this.base = base;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

        public String[] getAttributes() {
            return attributes;
        }

        public void setAttributes(String[] attributes) {
            this.attributes = attributes;
        }

        public SearchSettings getSettings(Object[] arguments) {
            return new SearchPropertiesSearchSettings(this, arguments);
        }
    }

    @ConfigurationProperties("groups")
    public static class GroupConfiguration implements Toggleable {

        public static final String PREFIX = LdapConfiguration.PREFIX + ".groups";

        private boolean enabled = false;
        private boolean subtree = true;
        private String base = "";
        private String filter = "uniquemember={0}";
        private String attribute = "cn";

        public boolean isSubtree() {
            return subtree;
        }

        public void setSubtree(boolean subtree) {
            this.subtree = subtree;
        }

        public String getBase() {
            return base;
        }

        public void setBase(String base) {
            this.base = base;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

        public String getAttribute() {
            return attribute;
        }

        public void setAttribute(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public SearchSettings getSearchSettings(Object[] arguments) {
            return new GroupPropertiesSearchSettings(this, arguments);
        }
    }

    @ConfigurationProperties("ssl")
    public static class SslConfiguration extends io.micronaut.http.ssl.SslConfiguration {

        public static final String PREFIX = LdapConfiguration.PREFIX + ".ssl";

        /**
         * Sets the key configuration.
         *
         * @param keyConfiguration The key configuration.
         */
        public void setKey(DefaultKeyConfiguration keyConfiguration) {
            super.setKey(keyConfiguration);
        }

        /**
         * Sets the key store.
         *
         * @param keyStoreConfiguration The key store configuration
         */
        void setKeyStore(DefaultKeyStoreConfiguration keyStoreConfiguration) {
            super.setKeyStore(keyStoreConfiguration);
        }

        /**
         * Sets trust store configuration.
         *
         * @param trustStore The trust store configuration
         */
        void setTrustStore(DefaultTrustStoreConfiguration trustStore) {
            super.setTrustStore(trustStore);
        }



        /**
         * The default {@link io.micronaut.http.ssl.SslConfiguration.KeyConfiguration}.
         */
        @SuppressWarnings("WeakerAccess")
        @ConfigurationProperties(KeyConfiguration.PREFIX)
        public static class DefaultKeyConfiguration extends KeyConfiguration {
        }


        /**
         * The default {@link io.micronaut.http.ssl.SslConfiguration.KeyStoreConfiguration}.
         */
        @SuppressWarnings("WeakerAccess")
        @ConfigurationProperties(KeyStoreConfiguration.PREFIX)
        public static class DefaultKeyStoreConfiguration extends KeyStoreConfiguration {

        }

        /**
         * The default {@link io.micronaut.http.ssl.SslConfiguration.TrustStoreConfiguration}.
         */
        @SuppressWarnings("WeakerAccess")
        @ConfigurationProperties(TrustStoreConfiguration.PREFIX)
        public static class DefaultTrustStoreConfiguration extends TrustStoreConfiguration {

        }
    }

}

package io.micronaut.security.ldap;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.Toggleable;
import io.micronaut.security.config.SecurityConfigurationProperties;
import io.micronaut.security.ldap.context.ContextPropertiesContextSettings;
import io.micronaut.security.ldap.context.ContextSettings;
import io.micronaut.security.ldap.context.SearchSettings;

import javax.inject.Inject;

@EachProperty(value = LdapConfigurationProperties.PREFIX, primary = "default")
public class LdapConfigurationProperties implements Toggleable {

    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".ldap";

    private boolean enabled = true;
    private ContextProperties context;
    private SearchProperties search;
    private GroupProperties group;
    private final String name;

    LdapConfigurationProperties(@Parameter String name) {
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
    public ContextProperties getContext() {
        return context;
    }

    /**
     * Sets the context configuration.
     *
     * @param contextProperties The context configuration
     */
    @Inject
    public void setContext(ContextProperties contextProperties) {
        this.context = contextProperties;
    }

    /**
     * @return The searchForUser configuration
     */
    public SearchProperties getSearch() {
        return search;
    }

    /**
     * Sets the searchForUser configuration.
     *
     * @param searchProperties The searchForUser configuration
     */
    @Inject
    public void setSearch(SearchProperties searchProperties) {
        this.search = searchProperties;
    }


    /**
     * @return The group configuration
     */
    public GroupProperties getGroup() {
        return group;
    }

    /**
     * Sets the group configuration.
     *
     * @param groupProperties The group configuration
     */
    @Inject
    public void setGroup(GroupProperties groupProperties) {
        this.group = groupProperties;
    }

    @ConfigurationProperties("context")
    public static class ContextProperties {

        public static final String PREFIX = LdapConfigurationProperties.PREFIX + ".context";

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

        public ContextSettings getSettings(String dn, String password) {
            return new ContextPropertiesContextSettings(this, dn, password);
        }

        public ContextSettings getManagerSettings() {
            return new ContextPropertiesContextSettings(this);
        }
    }

    @ConfigurationProperties("search")
    public static class SearchProperties {

        public static final String PREFIX = LdapConfigurationProperties.PREFIX + ".search";

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
    public static class GroupProperties implements Toggleable {

        public static final String PREFIX = LdapConfigurationProperties.PREFIX + ".groups";

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

}

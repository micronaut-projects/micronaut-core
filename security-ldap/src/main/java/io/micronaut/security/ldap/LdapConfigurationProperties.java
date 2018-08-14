package io.micronaut.security.ldap;

import com.sun.jndi.ldap.LdapCtxFactory;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.util.Toggleable;
import io.micronaut.security.config.SecurityConfigurationProperties;

import javax.inject.Inject;
import java.util.List;


@EachProperty(value = LdapConfigurationProperties.PREFIX, primary = "default")
public class LdapConfigurationProperties implements Toggleable {

    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".ldap";

    private boolean enabled = true;
    private ContextProperties context;
    private SearchProperties search;

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
        if (contextProperties != null) {
            this.context = contextProperties;
        }
    }

    /**
     * @return The search configuration
     */
    public SearchProperties getSearch() {
        return search;
    }

    /**
     * Sets the search configuration.
     *
     * @param searchProperties The search configuration
     */
    @Inject
    public void setSearch(SearchProperties searchProperties) {
        this.search = searchProperties;
    }

    @ConfigurationProperties("context")
    public static class ContextProperties {
        private String server;
        private String managerDn;
        private String managerPassword;
        private Class factory = LdapCtxFactory.class;

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

        public Class getFactory() {
            return factory;
        }

        public void setFactory(Class factory) {
            this.factory = factory;
        }
    }

    @ConfigurationProperties("search")
    public static class SearchProperties {
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
    }

}

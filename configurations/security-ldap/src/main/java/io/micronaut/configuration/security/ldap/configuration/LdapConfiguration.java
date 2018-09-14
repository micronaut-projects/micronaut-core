/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.security.ldap.configuration;

import io.micronaut.configuration.security.ldap.context.ContextConfigurationContextSettings;
import io.micronaut.configuration.security.ldap.context.ContextSettings;
import io.micronaut.configuration.security.ldap.context.SearchSettings;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.Toggleable;
import io.micronaut.security.config.SecurityConfigurationProperties;

/**
 * Configuration for LDAP authentication.
 *
 * @author James Kleeh
 * @since 1.0
 */
@EachProperty(value = LdapConfiguration.PREFIX, primary = "default")
public class LdapConfiguration implements Toggleable {

    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".ldap";

    private static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;
    private ContextConfiguration context;
    private SearchConfiguration search;
    private GroupConfiguration group;
    private final String name;

    /**
     * @param name The name of the configuration
     */
    LdapConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return The name of the configuration
     */
    public String getName() {
        return name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this configuration is enabled. Default {@value DEFAULT_ENABLED}.
     *
     * @param enabled The enabled setting
     */
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
     * @return The search configuration
     */
    public SearchConfiguration getSearch() {
        return search;
    }

    /**
     * Sets the search configuration.
     *
     * @param searchConfiguration The search configuration
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
     * Returns settings for creating a context for a given dn and password.
     *
     * @param dn       The user dn to bind with
     * @param password The user password to bind with
     * @return Settings to use to create a context
     */
    public ContextSettings getSettings(String dn, String password) {
        return new ContextConfigurationContextSettings(this, dn, password);
    }

    /**
     * @return Settings for creating a context for the manager.
     */
    public ContextSettings getManagerSettings() {
        return new ContextConfigurationContextSettings(this);
    }

    /**
     * The context configuration.
     */
    @ConfigurationProperties("context")
    public static class ContextConfiguration {

        /**
         * The configuration prefix.
         */
        public static final String PREFIX = LdapConfiguration.PREFIX + ".context";

        private static final String DEFAULT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

        private String server;
        private String managerDn;
        private String managerPassword;
        private String factory = DEFAULT_FACTORY;

        /**
         * @return The ldap server URL
         */
        public String getServer() {
            return server;
        }

        /**
         * Sets the server URL.
         *
         * @param server The server URL
         */
        public void setServer(String server) {
            this.server = server;
        }

        /**
         * @return The manager DN
         */
        public String getManagerDn() {
            return managerDn;
        }

        /**
         * Sets the manager DN.
         *
         * @param managerDn The manager DN
         */
        public void setManagerDn(String managerDn) {
            this.managerDn = managerDn;
        }

        /**
         * @return The manager password
         */
        public String getManagerPassword() {
            return managerPassword;
        }

        /**
         * Sets the manager password.
         *
         * @param managerPassword The manager password
         */
        public void setManagerPassword(String managerPassword) {
            this.managerPassword = managerPassword;
        }

        /**
         * @return The context factory class
         */
        public String getFactory() {
            return factory;
        }

        /**
         * Sets the context factory class. Default {@value DEFAULT_FACTORY}
         *
         * @param factory The factory class
         */
        public void setFactory(String factory) {
            this.factory = factory;
        }
    }

    /**
     * The user search configuration.
     */
    @ConfigurationProperties("search")
    public static class SearchConfiguration {

        /**
         * The configuration prefix.
         */
        public static final String PREFIX = LdapConfiguration.PREFIX + ".search";

        private static final boolean DEFAULT_SUBTREE = true;
        private static final String DEFAULT_FILTER = "(uid={0})";

        private boolean subtree = DEFAULT_SUBTREE;
        private String base = "";
        private String filter = DEFAULT_FILTER;
        private String[] attributes = null;

        /**
         * @return True if the subtree should be searched
         */
        public boolean isSubtree() {
            return subtree;
        }

        /**
         * Sets if the subtree should be searched. Default {@value DEFAULT_SUBTREE}
         *
         * @param subtree The subtree
         */
        public void setSubtree(boolean subtree) {
            this.subtree = subtree;
        }

        /**
         * @return The base DN to search from
         */
        public String getBase() {
            return base;
        }

        /**
         * Sets the base DN to search.
         *
         * @param base The base DN
         */
        public void setBase(String base) {
            this.base = base;
        }

        /**
         * @return The search filter
         */
        public String getFilter() {
            return filter;
        }

        /**
         * Sets the search filter. Default {@value DEFAULT_FILTER}
         *
         * @param filter The search filter
         */
        public void setFilter(String filter) {
            this.filter = filter;
        }

        /**
         * @return The attributes to return. Null if all
         */
        public String[] getAttributes() {
            return attributes;
        }

        /**
         * Sets the attributes to return. Default all
         *
         * @param attributes The attributes
         */
        public void setAttributes(String[] attributes) {
            this.attributes = attributes;
        }

        /**
         * @param arguments The search arguments
         * @return The settings to search for a user
         */
        public SearchSettings getSettings(Object[] arguments) {
            return new SearchPropertiesSearchSettings(this, arguments);
        }
    }

    /**
     * The group configuration.
     */
    @ConfigurationProperties("groups")
    public static class GroupConfiguration implements Toggleable {

        public static final String PREFIX = LdapConfiguration.PREFIX + ".groups";

        private static final boolean DEFAULT_ENABLED = false;
        private static final boolean DEFAULT_SUBTREE = true;
        private static final String DEFAULT_FILTER = "uniquemember={0}";
        private static final String DEFAULT_ATTR = "cn";

        private boolean enabled = DEFAULT_ENABLED;
        private boolean subtree = DEFAULT_SUBTREE;
        private String base = "";
        private String filter = DEFAULT_FILTER;
        private String attribute = DEFAULT_ATTR;

        /**
         * @return True if the subtree should be searched
         */
        public boolean isSubtree() {
            return subtree;
        }

        /**
         * Sets if the subtree should be searched. Default {@value DEFAULT_SUBTREE}
         *
         * @param subtree The subtree
         */
        public void setSubtree(boolean subtree) {
            this.subtree = subtree;
        }

        /**
         * @return The base DN to search from
         */
        public String getBase() {
            return base;
        }

        /**
         * Sets the base DN to search from.
         *
         * @param base The base DN
         */
        public void setBase(String base) {
            this.base = base;
        }

        /**
         * @return The filter used to search for groups
         */
        public String getFilter() {
            return filter;
        }

        /**
         * Sets the group search filter. Default {@value DEFAULT_FILTER}
         *
         * @param filter The filter
         */
        public void setFilter(String filter) {
            this.filter = filter;
        }

        /**
         * @return Which attribute is the group name
         */
        public String getAttribute() {
            return attribute;
        }

        /**
         * Sets the group attribute name. Default {@value DEFAULT_ATTR}
         *
         * @param attribute The attribute name
         */
        public void setAttribute(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets if group search is enabled. Default {@value DEFAULT_ENABLED}
         *
         * @param enabled The enabled setting
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @param arguments The search arguments
         * @return Settings to search ldap groups
         */
        public SearchSettings getSearchSettings(Object[] arguments) {
            return new GroupPropertiesSearchSettings(this, arguments);
        }
    }
}

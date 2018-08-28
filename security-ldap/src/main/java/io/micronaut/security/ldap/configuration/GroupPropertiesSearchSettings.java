package io.micronaut.security.ldap.configuration;

import io.micronaut.security.ldap.context.SearchSettings;

public class GroupPropertiesSearchSettings implements SearchSettings {

    private final LdapConfiguration.GroupConfiguration properties;
    private final Object[] arguments;

    GroupPropertiesSearchSettings(LdapConfiguration.GroupConfiguration properties, Object[] arguments) {
        this.properties = properties;
        this.arguments = arguments;
    }

    @Override
    public boolean isSubtree() {
        return properties.isSubtree();
    }

    @Override
    public String getBase() {
        return properties.getBase();
    }

    @Override
    public String getFilter() {
        return properties.getFilter();
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public String[] getAttributes() {
        return new String[] { properties.getAttribute() };
    }
}

package io.micronaut.security.ldap;

import io.micronaut.security.ldap.context.SearchSettings;

public class GroupPropertiesSearchSettings implements SearchSettings {

    private final LdapConfigurationProperties.GroupProperties properties;
    private final Object[] arguments;

    GroupPropertiesSearchSettings(LdapConfigurationProperties.GroupProperties properties, Object[] arguments) {
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

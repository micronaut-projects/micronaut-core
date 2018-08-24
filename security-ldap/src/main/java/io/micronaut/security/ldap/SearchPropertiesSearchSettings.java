package io.micronaut.security.ldap;

import io.micronaut.security.ldap.context.SearchSettings;

public class SearchPropertiesSearchSettings implements SearchSettings {

    private final LdapConfigurationProperties.SearchProperties properties;
    private final Object[] arguments;

    SearchPropertiesSearchSettings(LdapConfigurationProperties.SearchProperties properties, Object[] arguments) {
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
        return properties.getAttributes();
    }
}

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

import io.micronaut.configuration.security.ldap.context.SearchSettings;

/**
 * Implementation of {@link SearchSettings} that derives values from an
 * instance of {@link LdapConfiguration.GroupConfiguration}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class GroupPropertiesSearchSettings implements SearchSettings {

    private final LdapConfiguration.GroupConfiguration properties;
    private final Object[] arguments;

    /**
     * @param properties The group configuration
     * @param arguments  The arguments for the filter
     */
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

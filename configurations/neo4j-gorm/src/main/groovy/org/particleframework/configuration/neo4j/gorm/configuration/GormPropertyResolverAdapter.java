/*
 * Copyright 2018 original authors
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
package org.particleframework.configuration.neo4j.gorm.configuration;

import org.grails.datastore.mapping.config.Settings;
import org.particleframework.context.env.PropertyPlaceholderResolver;
import org.particleframework.core.value.PropertyResolver;
import org.particleframework.spring.core.env.PropertyResolverAdapter;

/**
 * Resolves default settings for GORM
 *
 * @author graemerocher
 * @since 1.0
 */
public class GormPropertyResolverAdapter extends PropertyResolverAdapter{
    public GormPropertyResolverAdapter(PropertyResolver propertyResolver, PropertyPlaceholderResolver placeholderResolver) {
        super(propertyResolver, placeholderResolver);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        T v = super.getProperty(key, targetType, defaultValue);
        if(v == null && isKeyFailedOnError(key) && boolean.class.isAssignableFrom(targetType)) {
            return (T) Boolean.TRUE;
        }
        return v;
    }

    protected boolean isKeyFailedOnError(String key) {
        return key.equalsIgnoreCase(Settings.SETTING_FAIL_ON_ERROR) || key.equalsIgnoreCase(org.grails.datastore.gorm.neo4j.config.Settings.PREFIX + ".failOnError");
    }
}

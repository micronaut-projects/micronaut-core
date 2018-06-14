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

package io.micronaut.configuration.gorm.configuration;

import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.spring.core.env.PropertyResolverAdapter;
import org.grails.datastore.mapping.config.Settings;

/**
 * @author graemerocher
 * @since 1.0
 */
public class GormPropertyResolverAdapter extends PropertyResolverAdapter {

    /**
     * Constructor.
     *
     * @param propertyResolver    The property resolver
     * @param placeholderResolver The property placeholder resolver
     */
    public GormPropertyResolverAdapter(PropertyResolver propertyResolver, PropertyPlaceholderResolver placeholderResolver) {
        super(propertyResolver, placeholderResolver);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        if (Settings.SETTING_FAIL_ON_ERROR.equals(key) && defaultValue == null) {
            return (T) super.getProperty(key, Boolean.class, Boolean.TRUE);
        } else {
            return super.getProperty(key, targetType, defaultValue);
        }
    }
}

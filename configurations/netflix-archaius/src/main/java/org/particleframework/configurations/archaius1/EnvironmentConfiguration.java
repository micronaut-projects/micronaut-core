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
package org.particleframework.configurations.archaius1;

import org.apache.commons.configuration.AbstractConfiguration;
import org.particleframework.context.env.Environment;
import org.particleframework.context.env.PropertySource;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.runtime.context.scope.refresh.RefreshEvent;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Adapts the {@link Environment} to the {@link AbstractConfiguration} type and fires change events when the application is refreshed
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
class EnvironmentConfiguration extends AbstractConfiguration implements ApplicationEventListener<RefreshEvent> {
    private final Environment environment;

    public EnvironmentConfiguration(Environment environment) {
        this.environment = environment;
    }

    public Environment getEnvironment() {
        return environment;
    }

    @Override
    protected void addPropertyDirect(String key, Object value) {
        // no-op
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(String key) {
        return environment.containsProperty(key);
    }

    @Override
    public Object getProperty(String key) {
        return environment.getProperty(key, Object.class).orElse(null);
    }

    @Override
    public Iterator<String> getKeys() {
        Iterator<PropertySource> propertySourceIterator = environment.getPropertySources().iterator();
        if(!propertySourceIterator.hasNext()) {
            return Collections.emptyIterator();
        }

        return new Iterator<String>() {
            Iterator<String> i = propertySourceIterator.next().iterator();
            @Override
            public boolean hasNext() {
                if(i.hasNext()) {
                    return true;
                }
                else {
                    if(propertySourceIterator.hasNext()) {
                        i = propertySourceIterator.next().iterator();
                    }
                    return i.hasNext();
                }
            }

            @Override
            public String next() {
                return i.next();
            }
        };
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        Map<String, Object> changedProperties = event.getSource();
        for (Map.Entry<String, Object> entry : changedProperties.entrySet()) {
            fireEvent(EVENT_SET_PROPERTY, entry.getKey(), entry.getValue(), false);
        }
    }
}

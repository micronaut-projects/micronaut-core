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

package io.micronaut.configuration.ribbon;

import com.netflix.client.VipAddressResolver;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;

import javax.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract implementation of the {@link IClientConfig} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unchecked")
public abstract class AbstractRibbonClientConfig implements IClientConfig {

    /**
     * The prefix to use for all Ribbon settings.
     */
    public static final String PREFIX = "ribbon";

    private final Environment environment;
    private Map<IClientConfigKey, Object> customSettings = new ConcurrentHashMap<>();
    private VipAddressResolver resolver = null;

    /**
     * Constructor.
     * @param environment environment
     */
    public AbstractRibbonClientConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Sets an optional {@link VipAddressResolver}.
     *
     * @param resolver The {@link VipAddressResolver}
     */
    @Inject
    public void setVipAddressResolver(Optional<VipAddressResolver> resolver) {
        if (resolver.isPresent()) {
            this.resolver = resolver.get();
        }
    }

    /**
     * @see IClientConfig#getClientName()
     */
    @Override
    public String getClientName() {
        return "default";
    }

    /**
     * @see IClientConfig#getNameSpace()
     */
    @Override
    public String getNameSpace() {
        return PREFIX;
    }

    /**
     * @see IClientConfig#loadProperties(String)
     */
    @Override
    public void loadProperties(String clientName) {
        // no-op, unnecessary
    }

    /**
     * @see IClientConfig#loadDefaultValues()
     */
    @Override
    public void loadDefaultValues() {
        // no-op, unnecessary
    }

    /**
     * @see IClientConfig#getProperties()
     */
    @Override
    public Map<String, Object> getProperties() {
        Map map = environment.getProperty(getNameSpace(), Argument.of(Map.class, String.class, Object.class)).orElse(Collections.EMPTY_MAP);
        Map<String, Object> all = new LinkedHashMap<>(map);
        for (Map.Entry<IClientConfigKey, Object> entry : customSettings.entrySet()) {
            all.put(entry.getKey().key(), entry.getValue());
        }
        return all;
    }

    /**
     * @see IClientConfig#setProperty(IClientConfigKey, Object)
     */
    @Override
    @Deprecated
    public void setProperty(IClientConfigKey key, Object value) {
        set(key, value);
    }

    /**
     * @see IClientConfig#getProperty(IClientConfigKey)
     */
    @Override
    @Deprecated
    public Object getProperty(IClientConfigKey key) {
        return get(key, null);
    }

    /**
     * @see IClientConfig#getProperty(IClientConfigKey, Object)
     */
    @Override
    @Deprecated
    public Object getProperty(IClientConfigKey key, Object defaultVal) {
        return get(key, defaultVal);
    }

    /**
     * @see IClientConfig#containsProperty(IClientConfigKey)
     */
    @Override
    public boolean containsProperty(IClientConfigKey key) {
        return key != null && (customSettings.containsKey(key) || environment.get(qualifyKey(key), key.type()).isPresent());
    }

    /**
     * @see IClientConfig#getPropertyAsInteger(IClientConfigKey, int)
     */
    @Override
    public int getPropertyAsInteger(IClientConfigKey key, int defaultValue) {
        return get(key, Integer.class, defaultValue);
    }

    /**
     * @see IClientConfig#getPropertyAsString(IClientConfigKey, String)
     */
    @Override
    public String getPropertyAsString(IClientConfigKey key, String defaultValue) {
        return get(key, String.class, defaultValue);
    }

    /**
     * @see IClientConfig#getPropertyAsBoolean(IClientConfigKey, boolean)
     */
    @Override
    public boolean getPropertyAsBoolean(IClientConfigKey key, boolean defaultValue) {
        return get(key, Boolean.class, defaultValue);
    }

    /**
     * @see IClientConfig#get(IClientConfigKey)
     */
    @Override
    public <T> T get(IClientConfigKey<T> key) {
        return get(key, null);
    }

    /**
     * @see IClientConfig#get(IClientConfigKey, Object)
     */
    @Override
    public <T> T get(IClientConfigKey<T> key, T defaultValue) {
        Class<T> type = key.type();
        return get(key, type, defaultValue);
    }

    /**
     * @see IClientConfig#set(IClientConfigKey, Object)
     */
    @Override
    public <T> IClientConfig set(IClientConfigKey<T> key, T value) {
        if (key != null) {
            if (value == null) {
                customSettings.remove(key);
            } else {
                customSettings.put(key, value);
            }
        }
        return this;
    }

    /**
     * @see IClientConfig#resolveDeploymentContextbasedVipAddresses()
     */
    @Override
    public String resolveDeploymentContextbasedVipAddresses() {
        String deploymentContextBasedVipAddressesMacro = (String) getProperty(CommonClientConfigKey.DeploymentContextBasedVipAddresses);
        if (deploymentContextBasedVipAddressesMacro == null) {
            return null;
        }
        return getVipAddressResolver().resolve(deploymentContextBasedVipAddressesMacro, this);
    }

    /**
     * Get a property based on the parameters.
     * @param key key
     * @param type type
     * @param defaultValue defaultValue
     * @param <T> type of config key
     * @return The property
     */
    protected <T> T get(IClientConfigKey<T> key, Class<T> type, T defaultValue) {
        if (key == null) {
            return null;
        }
        if (customSettings.containsKey(key)) {
            return ConversionService.SHARED.convert(customSettings.get(key), type).orElse(defaultValue);
        } else {
            return environment.getProperty(qualifyKey(key), type, defaultValue);
        }
    }

    /**
     * Return the namespace + key.
     * @param key key
     * @return concatenated result
     */
    protected String qualifyKey(IClientConfigKey key) {
        String property = NameUtils.hyphenate(key.key());
        return getNameSpace() + "." + property;
    }

    private VipAddressResolver getVipAddressResolver() {
        if (resolver == null) {
            synchronized (this) {
                if (resolver == null) {
                    resolver = InstantiationUtils.instantiate((String) getProperty(CommonClientConfigKey.VipAddressResolverClassName), VipAddressResolver.class);
                }
            }
        }
        return resolver;
    }
}

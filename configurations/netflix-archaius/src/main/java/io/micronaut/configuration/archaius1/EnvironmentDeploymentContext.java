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

package io.micronaut.configuration.archaius1;

import com.netflix.config.ConfigurationBasedDeploymentContext;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext;
import com.netflix.config.DynamicPropertyFactory;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.event.ServiceStartedEvent;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link com.netflix.config.DeploymentContext} that bridges to the current {@link Environment}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Context
public class EnvironmentDeploymentContext implements DeploymentContext, ApplicationEventListener<ServiceStartedEvent>, Closeable {

    private final Environment environment;
    private ServiceInstance instance;
    private Map<ContextKey, String> customSettings = new ConcurrentHashMap<>();

    /**
     * Constructor.
     * @param environment environment
     */
    public EnvironmentDeploymentContext(EnvironmentConfiguration environment) {
        this.environment = environment.getEnvironment();
        if (!ConfigurationManager.isConfigurationInstalled()) {
            ConfigurationManager.install(environment);
        }
    }

    @Override
    public String getDeploymentEnvironment() {
        if (customSettings.containsKey(ContextKey.environment)) {
            return customSettings.get(ContextKey.environment);
        }
        Set<String> activeNames = environment.getActiveNames();
        if (!activeNames.isEmpty()) {
            return activeNames.iterator().next();
        }
        return Environment.DEFAULT_NAME;
    }

    @Override
    public void setDeploymentEnvironment(String env) {
        setContextValue(ContextKey.environment, env);
    }

    @Override
    public String getDeploymentDatacenter() {
        if (customSettings.containsKey(ContextKey.datacenter)) {
            return customSettings.get(ContextKey.datacenter);
        } else if (instance != null) {
            return instance.getZone().orElse(null);
        }
        return null;
    }

    @Override
    public void setDeploymentDatacenter(String deployedAt) {
        setContextValue(ContextKey.datacenter, deployedAt);
    }

    @Override
    public String getApplicationId() {
        if (customSettings.containsKey(ContextKey.appId)) {
            return customSettings.get(ContextKey.appId);
        } else if (instance != null) {
            return instance.getId();
        }
        return Environment.DEFAULT_NAME;
    }

    @Override
    public void setApplicationId(String appId) {
        setContextValue(ContextKey.appId, appId);
    }

    @Override
    public void setDeploymentServerId(String serverId) {
        setContextValue(ContextKey.serverId, serverId);
    }

    @Override
    public String getDeploymentServerId() {
        if (customSettings.containsKey(ContextKey.serverId)) {
            return customSettings.get(ContextKey.serverId);
        } else if (instance != null) {
            return instance.getInstanceId().orElse(null);
        }
        return null;
    }

    @Override
    public String getDeploymentStack() {
        return customSettings.get(ContextKey.stack);
    }

    @Override
    public String getValue(ContextKey key) {
        switch (key) {
            case appId:
                return getApplicationId();
            case zone:
                return getZone();
            case environment:
                return getDeploymentEnvironment();
            case stack:
                return getDeploymentStack();
            case region:
                return getDeploymentRegion();
            case serverId:
                return getDeploymentServerId();
            case datacenter:
                return getDeploymentDatacenter();
            default:
                break;
        }
        return null;
    }

    @Override
    public void setValue(ContextKey key, String value) {
        switch (key) {
            case datacenter:
                setDeploymentDatacenter(value);
                break;
            case region:
                setDeploymentRegion(value);
                break;
            case stack:
                setDeploymentStack(value);
                break;
            default:
                break;        }
    }

    @Override
    public void setDeploymentStack(String stack) {
        setContextValue(ContextKey.stack, stack);
    }

    @Override
    public String getDeploymentRegion() {
        if (customSettings.containsKey(ContextKey.region)) {
            return customSettings.get(ContextKey.region);
        } else if (instance != null) {
            return instance.getRegion().orElse(null);
        }
        return null;
    }

    /**
     * @return The availability zone
     */
    public String getZone() {
        if (customSettings.containsKey(ContextKey.zone)) {
            return customSettings.get(ContextKey.zone);
        } else if (instance != null) {
            return instance.getZone().orElse(null);
        }
        return null;
    }

    @Override
    public void setDeploymentRegion(String region) {
        setContextValue(ContextKey.region, region);
    }

    @Override
    public void onApplicationEvent(ServiceStartedEvent event) {
        this.instance = event.getSource();
        ConfigurationManager.setDeploymentContext(
            this
        );
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        // cleanup static references
        ReflectionUtils.setFieldIfPossible(ConfigurationManager.class, "instance", null);
        ReflectionUtils.setFieldIfPossible(DynamicPropertyFactory.class, "config", null);
        ReflectionUtils.setFieldIfPossible(ConfigurationManager.class, "configMBean", null);
        ReflectionUtils.setFieldIfPossible(ConfigurationManager.class, "context", new ConfigurationBasedDeploymentContext());
        ReflectionUtils.setFieldIfPossible(ConfigurationManager.class, "customConfigurationInstalled", false);
    }

    private void setContextValue(ContextKey key, String env) {
        if (env == null) {
            customSettings.remove(key);
        } else {
            customSettings.put(key, env);
        }
    }
}

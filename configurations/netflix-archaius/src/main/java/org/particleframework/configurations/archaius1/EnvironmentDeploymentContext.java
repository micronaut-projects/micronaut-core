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

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext;
import com.netflix.config.DynamicPropertyFactory;
import org.particleframework.context.annotation.Context;
import org.particleframework.context.env.Environment;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.event.ServiceStartedEvent;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A {@link com.netflix.config.DeploymentContext} that bridges to the current {@link Environment}
 *
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@Context
public class EnvironmentDeploymentContext implements DeploymentContext, ApplicationEventListener<ServiceStartedEvent>, Closeable{

    private final Environment environment;
    private ServiceInstance instance;
    private String deploymentStack;
    private String region;
    private String deployedAt;

    public EnvironmentDeploymentContext(EnvironmentConfiguration environment) {
        this.environment = environment.getEnvironment();
        if(!ConfigurationManager.isConfigurationInstalled()) {
            ConfigurationManager.install(environment);
        }
    }

    @Override
    public String getDeploymentEnvironment() {
        Set<String> activeNames = environment.getActiveNames();
        if(!activeNames.isEmpty()) {
            return activeNames.iterator().next();
        }
        return Environment.DEFAULT_NAME;
    }

    @Override
    public void setDeploymentEnvironment(String env) {
        // no-op
    }

    @Override
    public String getDeploymentDatacenter() {
        if(deployedAt == null && instance != null) {
            return instance.getZone().orElse(null);
        }
        return deployedAt;
    }

    @Override
    public void setDeploymentDatacenter(String deployedAt) {
        this.deployedAt = deployedAt;
    }

    @Override
    public String getApplicationId() {
        if(instance != null) {
            return instance.getId();
        }
        return Environment.DEFAULT_NAME;
    }

    @Override
    public void setApplicationId(String appId) {

    }

    @Override
    public void setDeploymentServerId(String serverId) {

    }

    @Override
    public String getDeploymentServerId() {
        if(instance != null) {
            return instance.getInstanceId().orElse(null);
        }
        return null;
    }

    @Override
    public String getDeploymentStack() {
        return deploymentStack;
    }

    @Override
    public String getValue(ContextKey key) {
        switch (key) {
            case appId: return getApplicationId();
            case zone: return getDeploymentDatacenter();
            case environment: return getDeploymentEnvironment();
            case stack: return getDeploymentStack();
            case region: return getDeploymentRegion();
            case serverId: return getDeploymentServerId();
            case datacenter: return getDeploymentDatacenter();
        }
        return null;
    }

    @Override
    public void setValue(ContextKey key, String value) {
        switch (key) {
            case datacenter:
                setDeploymentDatacenter(value); break;
            case region:
                setDeploymentRegion(value); break;
            case stack:
                setDeploymentStack(value); break;
        }
    }

    @Override
    public void setDeploymentStack(String stack) {
        this.deploymentStack = stack;
    }

    @Override
    public String getDeploymentRegion() {
        if(region == null && instance != null) {
            return instance.getRegion().orElse(null);
        }
        return region;
    }

    @Override
    public void setDeploymentRegion(String region) {
        this.region = region;
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
        ReflectionUtils.setFieldIfPossible(ConfigurationManager.class, "context", null);
        ReflectionUtils.setFieldIfPossible(ConfigurationManager.class, "customConfigurationInstalled", false);
    }
}

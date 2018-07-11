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

import com.netflix.loadbalancer.Server;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.health.HealthStatus;

/**
 * Adapts the {@link ServiceInstance} interface to Ribbon's {@link Server} class.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class RibbonServer extends Server {
    private final ServiceInstance instance;

    /**
     * Consructor.
     * @param instance serviceInstance
     */
    public RibbonServer(ServiceInstance instance) {
        super(instance.getId());

        instance.getZone().ifPresent(this::setZone);
        this.instance = instance;
        boolean isUp = instance.getHealthStatus().equals(HealthStatus.UP);
        setAlive(isUp);
        setPort(instance.getPort());
        setHost(instance.getHost());
        setReadyToServe(isUp);
    }

    @Override
    public String getHost() {
        return instance.getHost();
    }

    @Override
    public int getPort() {
        return instance.getPort();
    }

    @Override
    public String getScheme() {
        return instance.isSecure() ? "https" : "http";
    }

    @Override
    public MetaInfo getMetaInfo() {
        return new MetaInfo() {
            @Override
            public String getAppName() {
                return instance.getId();
            }

            @Override
            public String getServerGroup() {
                return instance.getGroup().orElse(null);
            }

            @Override
            public String getServiceIdForDiscovery() {
                return instance.getId();
            }

            @Override
            public String getInstanceId() {
                return instance.getInstanceId().orElse(null);
            }
        };
    }
}

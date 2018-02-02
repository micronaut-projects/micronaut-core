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
package org.particleframework.discovery.eureka;

import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.eureka.client.v2.InstanceInfo;

import java.net.URI;

/**
 * A {@link ServiceInstance} implementation for Eureka
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class EurekaServiceInstance implements ServiceInstance {
    private final InstanceInfo instanceInfo;
    private final URI uri;

    public EurekaServiceInstance(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
        this.uri = createURI(instanceInfo);
    }

    private URI createURI(InstanceInfo instanceInfo) {
        int securePort = instanceInfo.getSecurePort();
        if(securePort > 0) {
            int port = instanceInfo.getPort();
            String portStr = port > 0 ? ":" + port : "";
            return URI.create("https://" + instanceInfo.getHostName() + portStr);
        }
        else {
            int port = instanceInfo.getPort();
            String portStr = port > 0 ? ":" + port : "";
            return URI.create("http://" + instanceInfo.getHostName() + portStr);
        }
    }

    /**
     * @return The Eureka {@link InstanceInfo}
     */
    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    @Override
    public String getId() {
        return instanceInfo.getId();
    }

    @Override
    public URI getURI() {
        return this.uri;
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        return ConvertibleValues.of(instanceInfo.getMetadata());
    }

}

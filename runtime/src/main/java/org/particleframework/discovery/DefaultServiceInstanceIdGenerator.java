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
package org.particleframework.discovery;

import org.particleframework.context.env.Environment;
import org.particleframework.runtime.server.EmbeddedServerInstance;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.StringTokenizer;

/**
 * The default generate for Instance IDs
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultServiceInstanceIdGenerator implements ServiceInstanceIdGenerator {

    protected DefaultServiceInstanceIdGenerator() {
    }

    @Nonnull
    @Override
    public String generateId(Environment environment, ServiceInstance serviceInstance) {
        Optional<String> cloudFoundryId = environment.getProperty("vcap.application.instance_id", String.class);
        if(cloudFoundryId.isPresent()) {
            return cloudFoundryId.get();
        }
        else {
            StringJoiner joiner = new StringJoiner(":");
            String applicationName = serviceInstance.getId();

            joiner.add(applicationName);
            if(serviceInstance instanceof EmbeddedServerInstance) {
                EmbeddedServerInstance esi = (EmbeddedServerInstance) serviceInstance;
                Optional<String> id = esi.getEmbeddedServer().getApplicationConfiguration().getInstance().getId();
                if(id.isPresent()) {
                    joiner.add(id.get());
                }
                else {
                    joiner.add(String.valueOf(esi.getPort()));
                }
            }
            else {
                joiner.add(String.valueOf(serviceInstance.getPort()));
            }

            return joiner.toString();
        }
    }
}

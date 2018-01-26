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
package org.particleframework.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.particleframework.core.util.CollectionUtils;

import java.net.InetAddress;
import java.util.*;

/**
 * A service entry in Consul. See https://www.consul.io/api/catalog.html#service
 *
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public class NewServiceEntry extends AbstractServiceEntry {

    @JsonCreator
    public NewServiceEntry(@JsonProperty("Name") String serviceName) {
        super(serviceName);
    }

    private List<Check> checks = new ArrayList<>(1);

    /**
     * See https://www.consul.io/api/agent/service.html#checks
     *
     * @return The health checks to perform
     */
    public List<Check> getChecks() {
        return checks;
    }

    public void setChecks(List<Check> checks) {
        if(CollectionUtils.isNotEmpty(checks)) {
            this.checks = checks;
        }
    }

    public NewServiceEntry checks(List<Check> checks) {
        setChecks(checks);
        return this;
    }

    public NewServiceEntry check(Check check) {
        setChecks(Collections.singletonList(check));
        return this;
    }

    @Override
    public NewServiceEntry id(String id) {
        return (NewServiceEntry) super.id(id);
    }

    @Override
    public NewServiceEntry address(InetAddress address) {
        return (NewServiceEntry) super.address(address);
    }

    @Override
    public NewServiceEntry address(String address) {
        return (NewServiceEntry) super.address(address);
    }

    @Override
    public NewServiceEntry port(Integer port) {
        return (NewServiceEntry) super.port(port);
    }

    @Override
    public NewServiceEntry tags(List<String> tags) {
        return (NewServiceEntry) super.tags(tags);
    }

    public NewServiceEntry tags(String... tags) {
        return (NewServiceEntry) super.tags(Arrays.asList(tags));
    }
}

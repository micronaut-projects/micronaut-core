/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.eureka.client.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.List;

/**
 * Models application info exposed by Eureka.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@JsonRootName("application")
public class ApplicationInfo {

    private String name;

    private List<InstanceInfo> instances;

    /**
     * @param name      The name
     * @param instances The instances
     */
    @JsonCreator
    ApplicationInfo(@JsonProperty("name") String name, @JsonProperty("instance") List<InstanceInfo> instances) {
        this.name = name;
        this.instances = instances;
    }

    /**
     * @return The application name
     */
    public String getName() {
        return name;
    }

    /**
     * @return The instances of this application
     */
    public List<InstanceInfo> getInstances() {
        return instances;
    }

    @Override
    public String toString() {
        return "ApplicationInfo{" +
            "name='" + name + '\'' +
            ", instances=" + instances +
            '}';
    }
}

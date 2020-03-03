/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.spring.config.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  Spring Config Server Response.
 *
 *  @author Thiago Locatelli
 *  @since 1.0
 */
public class ConfigServerResponse {

    private String name;
    private String[] profiles = new String[0];
    private String label;
    private List<ConfigServerPropertySource> propertySources = new ArrayList<>();
    private String version;
    private String state;

    /**
     *
     * @param propertySource The property source to be added
     */
    public void add(ConfigServerPropertySource propertySource) {
        this.propertySources.add(propertySource);
    }

    /**
     *
     * @param propertySources The list of property sources to be added
     */
    public void addAll(List<ConfigServerPropertySource> propertySources) {
        this.propertySources.addAll(propertySources);
    }

    /**
     *
     * @param propertySource The property source to be added first
     */
    public void addFirst(ConfigServerPropertySource propertySource) {
        this.propertySources.add(0, propertySource);
    }

    /**
     *
     * @return The list of property sources
     */
    public List<ConfigServerPropertySource> getPropertySources() {
        return propertySources;
    }

    /**
     *
     * @return The name of the property source
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @param name The name of the property source to be set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return The label of the property source
     */
    public String getLabel() {
        return label;
    }

    /**
     *
     * @param label The label of the property source to be set
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     *
     * @return The list of profiles
     */
    public String[] getProfiles() {
        return profiles;
    }

    /**
     *
     * @param profiles The list of profiles to be set
     */
    public void setProfiles(String[] profiles) {
        this.profiles = profiles;
    }

    /**
     *
     * @return  The version
     */
    public String getVersion() {
        return version;
    }

    /**
     *
     * @param version The version to be set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     *
     * @return The state
     */
    public String getState() {
        return state;
    }

    /**
     *
     * @param state The state to be set
     */
    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "ConfigServerResponse [name=" + name + ", profiles=" + Arrays.asList(profiles)
                + ", label=" + label + ", propertySources=" + propertySources
                + ", version=" + version
                + ", state=" + state + "]";
    }

}

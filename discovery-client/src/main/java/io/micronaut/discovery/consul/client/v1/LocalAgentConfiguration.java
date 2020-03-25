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
package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.micronaut.core.annotation.Introspected;

import java.util.Map;


/**
 * Configuration and member information of the local agent. See https://www.consul.io/api/agent.html
 * @author Álvaro Sánchez-Mariscal
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
@Introspected
public class LocalAgentConfiguration {

    private Map<String, String> configuration;
    private Map<String, Object> debugConfiguration;
    private MemberEntry member;
    private Map<String, String> metadata;

    /**
     * @return Configuration for this agent
     */
    public Map<String, String> getConfiguration() {
        return configuration;
    }

    /**
     * @param configuration The configuration
     */
    @JsonProperty("Config")
    public void setConfiguration(Map<String, String> configuration) {
        this.configuration = configuration;
    }

    /**
     * @return Debug configuration for this agent
     */
    public Map<String, Object> getDebugConfiguration() {
        return debugConfiguration;
    }

    /**
     * @param debugConfiguration Debug configuration
     */
    @JsonProperty("DebugConfig")
    public void setDebugConfiguration(Map<String, Object> debugConfiguration) {
        this.debugConfiguration = debugConfiguration;
    }

    /**
     * @return A {@link MemberEntry} describing this agent
     */
    public MemberEntry getMember() {
        return member;
    }

    /**
     * @param member A {@link MemberEntry}
     */
    public void setMember(MemberEntry member) {
        this.member = member;
    }

    /**
     * @return Metadata for this agent
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     *
     * @param metadata Metadata
     */
    @JsonProperty("Meta")
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}

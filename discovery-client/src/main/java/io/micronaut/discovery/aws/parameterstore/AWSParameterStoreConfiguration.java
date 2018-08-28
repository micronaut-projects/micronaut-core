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

package io.micronaut.discovery.aws.parameterstore;

import io.micronaut.configuration.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.Toggleable;

/**
 * This is the configuration class for the AWSParameterStoreConfigClient for AWS Parameter Store based configuration.
 */
@Requires(env = Environment.AMAZON_EC2)
@Requires(property = AWSParameterStoreConfiguration.ENABLED, value = "true", defaultValue = "false")
@ConfigurationProperties(AWSParameterStoreConfiguration.CONFIGURATION_PREFIX)
public class AWSParameterStoreConfiguration extends AWSClientConfiguration implements Toggleable  {

    /**
     * Constant for whether AWS parameter store is enabled or not.
     */
    public static final String ENABLED = "aws.client.system-manager.parameterstore.enabled";
    /**
     * The perfix for configuration.
     */
    public static final String CONFIGURATION_PREFIX = "system-manager.parameterstore";

    private static final String PREFIX = "config";
    private static final String DEFAULT_PATH = "/" + PREFIX + "/";

    private boolean useSecureParameters = false;
    private String rootHierarchyPath;
    private Boolean enabled;

    /**
     * Enable or disable this feature.
     * @return enable or disable this feature.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable or disable this feature.
     * @param enabled enable this feature
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * This is the default for the root hierarchy on the parameter store. If empty will default to '/config/application'.
     * @return root level of parameter hierarchy
     */
    public String getRootHierarchyPath() {
        if (this.rootHierarchyPath == null) {
            return DEFAULT_PATH;
        }
        return rootHierarchyPath;
    }

    /**
     * This is the default for the root hierarchy on the parameter store. If empty will default to '/config/application'.
     * @param rootHierarchyPath root prefix used for all calls to get Parameter store values
     */
    public void setRootHierarchyPath(String rootHierarchyPath) {
        this.rootHierarchyPath = rootHierarchyPath;
    }

    /**
     * This will turn on or off auto-decryption via MKS for SecureString parameters.
     * If you set this to off you will not get unencrypted values.
     * @return use auto encryption on SecureString types
     */
    public boolean  getUseSecureParameters() {
        return useSecureParameters;
    }

    /**
     * This will turn on or off auto-decryption via MKS for SecureString parameters.
     * If you set this to off you will not get unencrypted values.
     *
     * @param useSecureParameters True if secure parameters should be used
     */
    public void setUseSecureParameters(boolean  useSecureParameters) {
        this.useSecureParameters = useSecureParameters;
    }
}

package io.micronaut.discovery.aws.parameterStore;

import io.micronaut.configurations.aws.AWSClientConfiguration;
import io.micronaut.configurations.aws.AWSConfiguration;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.ApplicationConfiguration;



@Requires(env= Environment.AMAZON_EC2)
@Requires(beans = AWSClientConfiguration.class)
@Requires(property = "aws.systemManager.parameterStore.enabled", value = "true", defaultValue = "false")
@ConfigurationProperties("systemManager.parameterStore")
public class AWSParameterStoreConfiguration extends AWSConfiguration {


    public static final String PREFIX = "config";
    public static final String DEFAULT_PATH = "/" + PREFIX + "/";

    String rootHierarchyPath;
    Boolean useSecureParameters = false;
    Boolean enabled;


    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRootHierarchyPath() {
        if (this.rootHierarchyPath ==null) {
            return DEFAULT_PATH;
        }
        return rootHierarchyPath;
    }

    public void setRootHierarchyPath(String rootHierarchyPath) {
        this.rootHierarchyPath = rootHierarchyPath;
    }

    public Boolean getUseSecureParameters() {
        return useSecureParameters;
    }

    public void setUseSecureParameters(Boolean useSecureParameters) {
        this.useSecureParameters = useSecureParameters;
    }
}

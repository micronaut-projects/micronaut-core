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
package org.particleframework.discovery.cloud.aws;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.env.Environment;
import org.particleframework.core.util.Toggleable;
import org.particleframework.runtime.ApplicationConfiguration;


/**
 * Default configuration for retrieving Amazon EC2 metadata
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(AmazonMetadataConfiguration.PREFIX)
@Requires(env = Environment.AMAZON_EC2)
public class AmazonMetadataConfiguration implements Toggleable {
    public static final String PREFIX = ApplicationConfiguration.PREFIX + "." + Environment.AMAZON_EC2 + ".metadata";

    private String url = "http://169.254.169.254/";
    private String metadataUrl;
    private String instanceDocumentUrl;
    private boolean enabled = true;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public String getMetadataUrl() {
        if(metadataUrl == null) {
            return url + "/latest/meta-data/";
        }
        return metadataUrl;
    }

    public String getInstanceDocumentUrl() {
        if(instanceDocumentUrl == null) {
            return url + "/latest/dynamic/instance-identity/document";
        }
        return instanceDocumentUrl;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    public void setInstanceDocumentUrl(String instanceDocumentUrl) {
        this.instanceDocumentUrl = instanceDocumentUrl;
    }
}

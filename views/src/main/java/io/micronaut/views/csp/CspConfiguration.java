/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.views.csp;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.micronaut.views.ViewsConfigurationProperties;

/**
 * Defines CSP configuration properties.
 *
 * @author Arul Dhesiaseelan
 * @since 1.1
 */
@ConfigurationProperties(CspConfiguration.PREFIX)
public class CspConfiguration implements Toggleable {

    /**
     * The prefix for csp configuration.
     */
    public static final String PREFIX = ViewsConfigurationProperties.PREFIX + ".csp";

    public static final boolean DEFAULT_ENABLED = false;

    private boolean enabled = DEFAULT_ENABLED;

    private String policyDirectives;

    private boolean reportOnly = false;

    /**
     * @return Whether csp is enabled. Defaults to false.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return Policy directives
     */
    public String getPolicyDirectives() {
        return policyDirectives;
    }

    /**
     * @return Whether reportOnly is set. Defaults to false.
     */
    public boolean isReportOnly() {
        return reportOnly;
    }

    /**
     * Sets whether CSP is enabled. Default value ({@value #DEFAULT_ENABLED})
     * @param enabled True if CSP is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the policy directives.
     * @param policyDirectives CSP policy directives
     */
    public void setPolicyDirectives(String policyDirectives) {
        this.policyDirectives = policyDirectives;
    }

    /**
     * Sets whether to include Content-Security-Policy-Report-Only header in the response,
     * defaults to Content-Security-Policy header. Default value ({@value #DEFAULT_ENABLED})
     * @param reportOnly set to true for reporting purpose only
     */
    public void setReportOnly(boolean reportOnly) {
        this.reportOnly = reportOnly;
    }

}

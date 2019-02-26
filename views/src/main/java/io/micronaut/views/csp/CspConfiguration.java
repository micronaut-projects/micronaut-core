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

import javax.annotation.Nullable;
import java.util.Optional;

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

    /**
     * The path for endpoints settings.
     */
    public static final String FILTER_PATH = PREFIX + ".filter-path";
    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_REPORT_ONLY = false;
    public static final String DEFAULT_FILTER_PATH = "/**";

    private boolean enabled = DEFAULT_ENABLED;
    private String policyDirectives;
    private boolean reportOnly = DEFAULT_REPORT_ONLY;
    private String filterPath = DEFAULT_FILTER_PATH;

    /**
     * @return Whether csp headers will be sent
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The policy directives
     */
    public Optional<String> getPolicyDirectives() {
        return Optional.of(policyDirectives);
    }

    /**
     * @return Whether the report only header should be set
     */
    public boolean isReportOnly() {
        return reportOnly;
    }

    /**
     * Sets whether CSP is enabled. Default {@value #DEFAULT_ENABLED}.
     *
     * @param enabled True if CSP is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the policy directives.
     * @param policyDirectives CSP policy directives
     */
    public void setPolicyDirectives(@Nullable String policyDirectives) {
        this.policyDirectives = policyDirectives;
    }

    /**
     * If true, the Content-Security-Policy-Report-Only header will be sent instead
     * of Content-Security-Policy. Default {@value #DEFAULT_REPORT_ONLY}.
     *
     * @param reportOnly set to true for reporting purpose only
     */
    public void setReportOnly(boolean reportOnly) {
        this.reportOnly = reportOnly;
    }

    /**
     * @return The path the CSP filter should apply to
     */
    public String getFilterPath() {
        return filterPath;
    }

    /**
     * Sets the path the CSP filter should apply to. Default value {@value #DEFAULT_FILTER_PATH}.
     *
     * @param filterPath The filter path
     */
    public void setFilterPath(String filterPath) {
        this.filterPath = filterPath;
    }
}

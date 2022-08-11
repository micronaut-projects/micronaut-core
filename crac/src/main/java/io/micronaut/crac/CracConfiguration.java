/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.crac;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Experimental;

/**
 * Configuration for CRaC support. Enabled by default, and requires the {@link io.micronaut.crac.support.CracCondition} to be true.
 * This can be disabled by setting the property here, and we can add a new class to check for the condition
 * (in case the proposed package or class changes in the CRaC JDK).
 *
 * @author Tim Yates
 * @since 3.7.0
 */
@Experimental
@ConfigurationProperties(CracConfiguration.PREFIX)
public class CracConfiguration {

    public static final String PREFIX = "crac";
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;
    private String cracCompatClass;

    /**
     * @return Whether CRaC is enabled.
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * Disable CRaC support even if we're on a supporting JDK.
     *
     * @param enabled override CRaC if required
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The class to look for to check CRaC compatibility.
     */
    public String getCracCompatClass() {
        return cracCompatClass;
    }

    /**
     * Set the class to look for to check CRaC compatibility.
     *
     * @param cracCompatClass the fully qualified class name.
     */
    public void setCracCompatClass(String cracCompatClass) {
        this.cracCompatClass = cracCompatClass;
    }

    @Override
    public String toString() {
        return "CracConfiguration{" +
            "enabled=" + enabled +
            ", cracCompatClass='" + cracCompatClass + '\'' +
            '}';
    }
}

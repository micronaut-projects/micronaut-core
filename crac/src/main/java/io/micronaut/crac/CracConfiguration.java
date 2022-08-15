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
import io.micronaut.core.util.Toggleable;

/**
 * Configuration for CRaC support. Enabled by default.
 *
 * @author Tim Yates
 * @since 3.7.0
 */
@Experimental
@ConfigurationProperties(CracConfiguration.PREFIX)
public class CracConfiguration implements Toggleable {

    /**
     * The prefix to use for CRaC configuration.
     */
    public static final String PREFIX = "crac";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return Whether CRaC is enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether CRaC (Coordinated Restore at Checkpoint) support, even if we're on a supporting JDK, is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled override CRaC if required
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

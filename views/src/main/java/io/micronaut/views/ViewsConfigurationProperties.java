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

package io.micronaut.views;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.StringUtils;

/**
 * Implementation of {@link ViewsConfiguration}. Views configuration properties.
 *
 * @author Sergio del Amo
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(ViewsConfigurationProperties.PREFIX)
public class ViewsConfigurationProperties implements ViewsConfiguration {

    /**
     * The prefix for view configuration.
     */
    public static final String PREFIX = "micronaut.views";

    /**
     * The default views folder.
     */
    public static final String DEFAULT_FOLDER = "views";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    private String folder = DEFAULT_FOLDER;

    /**
     * enabled getter.
     *
     * @return boolean flag indicating whether the security features are enabled.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * @return The resources folder where views should be searched for. By default {@value #DEFAULT_FOLDER}
     */
    @Override
    public String getFolder() {
        return this.folder;
    }

    /**
     * Whether view rendering is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled True if view rendering is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The folder to look for views.
     *
     * @param folder The folder
     */
    public void setFolder(String folder) {
        if (StringUtils.isNotEmpty(folder)) {
            this.folder = folder;
        }
    }

    /**
     * The folder to look for views. Default value ({@value #DEFAULT_FOLDER}).
     *
     * @param folder The folder
     */
    public void setDir(String folder) {
        if (StringUtils.isNotEmpty(folder)) {
            this.folder = folder;
        }
    }
}

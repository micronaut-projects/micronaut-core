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

package io.micronaut.views.handlebars;

import com.github.jknack.handlebars.Handlebars;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.views.ViewsConfigurationProperties;

/**
 * {@link ConfigurationProperties} implementation of {@link HandlebarsViewsRendererConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(classes = Handlebars.class)
@ConfigurationProperties(HandlebarsViewsRendererConfigurationProperties.PREFIX)
public class HandlebarsViewsRendererConfigurationProperties implements HandlebarsViewsRendererConfiguration {

    /**
     * The prefix to use.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String PREFIX = ViewsConfigurationProperties.PREFIX + ".handlebars";

    /**
     * The default extension.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_EXTENSION = "hbs";

    private boolean enabled = true;
    private String defaultExtension = DEFAULT_EXTENSION;

    /**
     * enabled getter.
     *
     * @return boolean flag indicating whether {@link HandlebarsViewsRenderer} is enabled.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * @return Default extension for templates. By default {@value #DEFAULT_EXTENSION}.
     */
    @Override
    public String getDefaultExtension() {
        return defaultExtension;
    }

    /**
     * Whether handlebars view rendering is enabled.
     *
     * @param enabled True if is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The default extension.
     *
     * @param defaultExtension The extension
     */
    public void setDefaultExtension(String defaultExtension) {
        if (StringUtils.isNotEmpty(defaultExtension)) {
            this.defaultExtension = defaultExtension;
        }
    }
}

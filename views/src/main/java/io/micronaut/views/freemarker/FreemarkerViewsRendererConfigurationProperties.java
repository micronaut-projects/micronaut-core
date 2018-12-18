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

package io.micronaut.views.freemarker;

import freemarker.template.Configuration;
import freemarker.template.Version;
import io.micronaut.context.annotation.*;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.views.ViewsConfiguration;
import io.micronaut.views.ViewsConfigurationProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@link ConfigurationProperties} implementation of {@link FreemarkerViewsRendererConfiguration}.
 * 
 * All configured properties are extracted from {@link freemarker.template.Configuration} and
 * {@link freemarker.core.Configurable}. All Freemarker properties names are reused in the micronaut
 * configuration.
 * 
 * If a value is not declared and is null, the default configuration from Freemarker is used. The expected
 * format of each value is the same from Freemarker, and no conversion or validation is done by Micronaut.
 * 
 * All Freemarker configuration documentation is published in their
 * <a href="https://freemarker.apache.org/docs/pgui_config.html">site</a>.
 * 
 * @author Jerónimo López
 * @since 1.1
 */
@Requires(classes = freemarker.template.Configuration.class)
@ConfigurationProperties(FreemarkerViewsRendererConfigurationProperties.PREFIX)
public class FreemarkerViewsRendererConfigurationProperties implements FreemarkerViewsRendererConfiguration {

    public static final String PREFIX = ViewsConfigurationProperties.PREFIX + ".freemarker";

    /**
     * The default extension.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_EXTENSION = "ftl";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    @ConfigurationBuilder
    final Configuration configuration;

    private boolean enabled = DEFAULT_ENABLED;
    private String defaultExtension = DEFAULT_EXTENSION;

    /**
     * Default contructor.
     *
     * @param viewsConfiguration The views configuration
     * @param version The minimum version
     * @param resourceLoader The resource loader
     */
    public FreemarkerViewsRendererConfigurationProperties(
            ViewsConfiguration viewsConfiguration,
            @Property(name = PREFIX + ".incompatible-improvements") @Nullable String version,
            @Nullable ClassPathResourceLoader resourceLoader) {
        this.configuration = new Configuration(version != null ? new Version(version) : Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        if (resourceLoader != null) {
            this.configuration.setClassLoaderForTemplateLoading(
                    resourceLoader.getClassLoader(), "/" + viewsConfiguration.getFolder()
            );
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether freemarker views are enabled.
     *
     * @param enabled True if they are.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The freemarker configuration
     */
    public @Nonnull Configuration getConfiguration() {
        return configuration;
    }

    /**
     * @return The default extension to use
     */
    public @Nonnull String getDefaultExtension() {
        return defaultExtension;
    }

    /**
     * Sets the default extension to use.
     * @param defaultExtension The default extension
     */
    public void setDefaultExtension(String defaultExtension) {
        ArgumentUtils.requireNonNull("defaultExtension", defaultExtension);
        this.defaultExtension = defaultExtension;
    }
}

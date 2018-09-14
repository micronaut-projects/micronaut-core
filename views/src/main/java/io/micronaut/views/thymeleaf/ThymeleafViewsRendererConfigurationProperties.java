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

package io.micronaut.views.thymeleaf;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.views.ViewsConfigurationProperties;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;

import java.time.Duration;

/**
 * {@link ConfigurationProperties} implementation of {@link ThymeleafViewsRendererConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(classes = TemplateEngine.class)
@ConfigurationProperties(ThymeleafViewsRendererConfigurationProperties.PREFIX)
public class ThymeleafViewsRendererConfigurationProperties implements ThymeleafViewsRendererConfiguration {

    /**
     * The prefix to use for configuration.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String PREFIX = ViewsConfigurationProperties.PREFIX + ".thymeleaf";

    /**
     * The default suffix.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_SUFFIX = ".html";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default character encoding value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_CHARACTER_ENCODING = "UTF-8";

    /**
     * The default force suffix.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_FORCESUFFIX = false;

    /**
     * The default force template mode.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_FORCETEMPLATEMODE = false;

    private boolean enabled = DEFAULT_ENABLED;
    private String characterEncoding = DEFAULT_CHARACTER_ENCODING;
    private TemplateMode templateMode = AbstractConfigurableTemplateResolver.DEFAULT_TEMPLATE_MODE;
    private String suffix = DEFAULT_SUFFIX;
    private boolean forceSuffix = DEFAULT_FORCESUFFIX;
    private boolean forceTemplateMode = DEFAULT_FORCETEMPLATEMODE;
    private boolean cacheable = AbstractConfigurableTemplateResolver.DEFAULT_CACHEABLE;
    private Long cacheTTLMs = AbstractConfigurableTemplateResolver.DEFAULT_CACHE_TTL_MS;
    private boolean checkExistence = AbstractConfigurableTemplateResolver.DEFAULT_EXISTENCE_CHECK;

    /**
     * @return the character encoding.
     *
     * @see AbstractConfigurableTemplateResolver#getCharacterEncoding()
     */
    @Override
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    /**
     * @return the template mode to be used.
     * @see AbstractConfigurableTemplateResolver#getTemplateMode()
     */
    @Override
    public TemplateMode getTemplateMode() {
        return this.templateMode;
    }

    /**
     * @return the suffix. Default value {@value #DEFAULT_SUFFIX}
     * @see AbstractConfigurableTemplateResolver#getSuffix()
     */
    @Override
    public String getSuffix() {
        return this.suffix;
    }

    /**
     * @return whether the suffix will be forced or not.
     * @see AbstractConfigurableTemplateResolver#getForceSuffix()
     */
    @Override
    public boolean getForceSuffix() {
        return this.forceSuffix;
    }

    /**
     * @return whether the suffix will be forced or not.
     * @see AbstractConfigurableTemplateResolver#getForceTemplateMode()
     */
    @Override
    public boolean getForceTemplateMode() {
        return this.forceTemplateMode;
    }

    /**
     * @return the cache TTL for resolved templates.
     * @see AbstractConfigurableTemplateResolver#getCacheTTLMs()
     */
    @Override
    public Long getCacheTTLMs() {
        return this.cacheTTLMs;
    }

    /**
     * @return <tt>true</tt> if resource existence will be checked, <tt>false</tt> if not
     * @see AbstractConfigurableTemplateResolver#getCheckExistence()
     */
    @Override
    public boolean getCheckExistence() {
        return this.checkExistence;
    }

    /**
     * @return whether templates resolved are cacheable or not.
     * @see AbstractConfigurableTemplateResolver#isCacheable()
     */
    @Override
    public boolean getCacheable() {
        return this.cacheable;
    }

    /**
     * enabled getter.
     *
     * @return boolean flag indicating whether {@link ThymeleafViewsRenderer} is enabled.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Sets whether thymeleaf rendering is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled True if is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the character encoding to use. Default value ({@value #DEFAULT_CHARACTER_ENCODING}).
     *
     * @param characterEncoding The character encoding
     */
    public void setCharacterEncoding(String characterEncoding) {
        if (StringUtils.isNotEmpty(characterEncoding)) {
            this.characterEncoding = characterEncoding;
        }
    }

    /**
     * Sets the template mode.
     *
     * @param templateMode The template mode
     */
    public void setTemplateMode(TemplateMode templateMode) {
        if (templateMode != null) {
            this.templateMode = templateMode;
        }
    }

    /**
     * Sets the suffix to use.
     *
     * @param suffix The suffix
     */
    public void setSuffix(String suffix) {
        if (StringUtils.isNotEmpty(suffix)) {
            this.suffix = suffix;
        }
    }

    /**
     * Sets whether to force the suffix. Default value ({@value #DEFAULT_FORCESUFFIX}).
     *
     * @param forceSuffix True if the suffix should be forced
     */
    public void setForceSuffix(boolean forceSuffix) {
        this.forceSuffix = forceSuffix;
    }

    /**
     * Sets whether to force template mode. Default value ({@value #DEFAULT_FORCETEMPLATEMODE}).
     *
     * @param forceTemplateMode True if template mode should be forced
     */
    public void setForceTemplateMode(boolean forceTemplateMode) {
        this.forceTemplateMode = forceTemplateMode;
    }

    /**
     * Sets whether templates are cacheable.
     *
     * @param cacheable True if they are cacheable
     */
    public void setCacheable(boolean cacheable) {
        this.cacheable = cacheable;
    }

    /**
     * Sets the cache TTL in millis.
     *
     * @param cacheTTLMs The cache millis
     */
    public void setCacheTTLMs(long cacheTTLMs) {
        this.cacheTTLMs = cacheTTLMs;
    }

    /**
     * Sets the cache TTL as a duration.
     *
     * @param duration The duration
     */
    public void setCacheTTL(Duration duration) {
        if (duration != null) {
            this.cacheTTLMs = duration.toMillis();
        }
    }

    /**
     * Sets whether templates should be checked for existence.
     *
     * @param checkExistence True if they should be
     */
    public void setCheckExistence(boolean checkExistence) {
        this.checkExistence = checkExistence;
    }
}

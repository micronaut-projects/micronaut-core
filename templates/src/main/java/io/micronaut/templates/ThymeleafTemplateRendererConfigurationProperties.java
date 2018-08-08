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

package io.micronaut.templates;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;

/**
 * {@link ConfigurationProperties} implementation of {@link ThymeleafTemplateRendererConfiguration}.
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(classes = TemplateEngine.class)
@ConfigurationProperties(ThymeleafTemplateRendererConfigurationProperties.PREFIX)
public class ThymeleafTemplateRendererConfigurationProperties implements ThymeleafTemplateRendererConfiguration {
    public static final String PREFIX = TemplatesConfigurationProperties.PREFIX + ".thymeleaf";

    protected boolean enabled = true;

    protected String characterEncoding = "UTF-8";

    protected TemplateMode templateMode = AbstractConfigurableTemplateResolver.DEFAULT_TEMPLATE_MODE;

    protected String suffix = ".html";
    protected boolean forceSuffix = false;
    protected boolean forceTemplateMode = false;
    protected boolean cacheable = AbstractConfigurableTemplateResolver.DEFAULT_CACHEABLE;
    protected Long cacheTTLMs = AbstractConfigurableTemplateResolver.DEFAULT_CACHE_TTL_MS;
    protected boolean checkExistence = AbstractConfigurableTemplateResolver.DEFAULT_EXISTENCE_CHECK;

    /**
     * @see {@link AbstractConfigurableTemplateResolver#getCharacterEncoding()}.
     *
     * @return the character encoding.
     */
    @Override
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    /**
     * @see {@link AbstractConfigurableTemplateResolver#getTemplateMode()}
     *
     * @return the template mode to be used.
     */
    @Override
    public TemplateMode getTemplateMode() {
        return this.templateMode;
    }

    /**
     * @see {@link AbstractConfigurableTemplateResolver#getSuffix()}
     *
     * @return the suffix.
     */
    @Override
    public String getSuffix() {
        return this.suffix;
    }

    /**
     * @see {@link AbstractConfigurableTemplateResolver#getForceSuffix()}
     *
     * @return whether the suffix will be forced or not.
     */
    @Override
    public boolean getForceSuffix() {
        return this.forceSuffix;
    }

    /**
     * @see {@link AbstractConfigurableTemplateResolver#getForceTemplateMode()}
     *
     * @return whether the suffix will be forced or not.
     */
    @Override
    public boolean getForceTemplateMode() {
        return this.forceTemplateMode;
    }

    /**
     * @see {@link AbstractConfigurableTemplateResolver#getCacheTTLMs()}
     *
     * @return the cache TTL for resolved templates.
     */
    @Override
    public Long getCacheTTLMs() {
        return this.cacheTTLMs;
    }

    /**
     * @see {@link AbstractConfigurableTemplateResolver#getCheckExistence()}
     *
     * @return <tt>true</tt> if resource existence will be checked, <tt>false</tt> if not
     */
    @Override
    public boolean getCheckExistence() {
        return this.checkExistence;
    }

    /**
     * @see {@link AbstractConfigurableTemplateResolver#isCacheable()}
     *
     * @return whether templates resolved are cacheable or not.
     */
    @Override
    public boolean getCacheable() {
        return this.cacheable;
    }

    /**
     * enabled getter.
     * @return boolean flag indicating whether {@link ThymeleafTemplateRenderer} is enabled.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}

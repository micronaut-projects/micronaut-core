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

import io.micronaut.core.util.Toggleable;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Configuration for {@link ThymeleafTemplateRenderer}.
 * @author Sergio del Amo
 * @since 1.0
 */
public interface ThymeleafTemplateRendererConfiguration extends Toggleable {

    /**
     * @see {@link org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getCharacterEncoding()}
     *
     * @return the character encoding.
     */
    String getCharacterEncoding();

    /**
     * @see {@link org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getTemplateMode()}
     *
     * @return the template mode to be used.
     */
    TemplateMode getTemplateMode();

    /**
     * @see {@link org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getSuffix()}
     *
     * @return the suffix.
     */
    String getSuffix();

    /**
     * @see {@link org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getForceSuffix()}
     *
     * @return whether the suffix will be forced or not.
     */
    boolean getForceSuffix();

    /**
     * @see {@link org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getForceTemplateMode()}
     *
     * @return whether the suffix will be forced or not.
     */
    boolean getForceTemplateMode();

    /**
     * @see {@link org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getCacheTTLMs()}
     *
     * @return the cache TTL for resolved templates.
     */
    Long getCacheTTLMs();

    /**
     * @see {@link org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getCheckExistence()}
     *
     * @return <tt>true</tt> if resource existence will be checked, <tt>false</tt> if not
     */
    boolean getCheckExistence();

    /**
     * @see {@link org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#isCacheable()}
     *
     * @return whether templates resolved are cacheable or not.
     */
    boolean getCacheable();
}

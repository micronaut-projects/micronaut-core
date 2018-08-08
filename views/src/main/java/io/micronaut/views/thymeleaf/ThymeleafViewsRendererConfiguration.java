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

import io.micronaut.core.util.Toggleable;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Configuration for {@link ThymeleafViewsRenderer}.
 *
 * @author Sergio del Amo
 * @author graemerocher
 * @since 1.0
 */
public interface ThymeleafViewsRendererConfiguration extends Toggleable {

    /**
     * @return the character encoding.
     * @see org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getCharacterEncoding()
     */
    String getCharacterEncoding();

    /**
     * @return the template mode to be used.
     * @see org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getTemplateMode()
     */
    TemplateMode getTemplateMode();

    /**
     * @return the suffix.
     * @see org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getSuffix()
     */
    String getSuffix();

    /**
     * @return whether the suffix will be forced or not.
     * @see org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getForceSuffix()
     */
    boolean getForceSuffix();

    /**
     * @return whether the suffix will be forced or not.
     * @see org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getForceTemplateMode()
     */
    boolean getForceTemplateMode();

    /**
     * @return the cache TTL for resolved templates.
     * @see org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getCacheTTLMs()
     */
    Long getCacheTTLMs();

    /**
     * @return <tt>true</tt> if resource existence will be checked, <tt>false</tt> if not
     * @see org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#getCheckExistence()
     */
    boolean getCheckExistence();

    /**
     * @return whether templates resolved are cacheable or not.
     * @see org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver#isCacheable()
     */
    boolean getCacheable();
}

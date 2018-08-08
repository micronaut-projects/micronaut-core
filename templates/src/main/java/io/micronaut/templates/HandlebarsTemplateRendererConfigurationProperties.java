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

/**
 * {@link ConfigurationProperties} implementation of {@link HandlebarsTemplateRendererConfiguration}.
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(classes = TemplateEngine.class)
@ConfigurationProperties(HandlebarsTemplateRendererConfigurationProperties.PREFIX)
public class HandlebarsTemplateRendererConfigurationProperties implements HandlebarsTemplateRendererConfiguration {
    public static final String PREFIX = TemplatesConfigurationProperties.PREFIX + ".handlebars";

    protected boolean enabled = true;

    /**
     * enabled getter.
     * @return boolean flag indicating whether {@link HandlebarsTemplateRenderer} is enabled.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}

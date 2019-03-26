/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.http.HttpRequest;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.EngineContext;
import org.thymeleaf.engine.TemplateData;

import java.util.Locale;
import java.util.Map;

/**
 * Implementation of the {@link org.thymeleaf.context.IEngineContext} interface for web processing.
 *
 * @author Semyon Gashchenko
 * @since 1.1.0
 */
public class WebEngineContext extends EngineContext {

    private final HttpRequest<?> request;

    /**
     * @param configuration the configuration instance being used.
     * @param templateData the template data for the template to be processed.
     * @param templateResolutionAttributes the template resolution attributes.
     * @param request HTTP request.
     * @param locale the locale.
     * @param variables the context variables, probably coming from another {@link
     * org.thymeleaf.context.IContext} implementation.
     * @see EngineContext#EngineContext(IEngineConfiguration, TemplateData, Map, Locale, Map).
     */
    public WebEngineContext(
            IEngineConfiguration configuration, TemplateData templateData,
            Map<String, Object> templateResolutionAttributes, HttpRequest<?> request, Locale locale,
            Map<String, Object> variables) {
        super(configuration, templateData, templateResolutionAttributes, locale, variables);
        this.request = request;
    }

    /**
     * @return HTTP request.
     */
    public HttpRequest<?> getRequest() {
        return request;
    }
}

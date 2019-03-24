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

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.EngineContext;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.IEngineContextFactory;
import org.thymeleaf.engine.TemplateData;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of the {@link IEngineContextFactory} interface for {@link WebContext} support.
 *
 * @author Semyon Gashchenko
 */
@Singleton
public class EngineContextFactory implements IEngineContextFactory {

    @Override
    public IEngineContext createEngineContext(
            IEngineConfiguration configuration, TemplateData templateData,
            Map<String, Object> templateResolutionAttributes, IContext context) {
        Objects.requireNonNull(context, "Context object cannot be null");
        Set<String> variableNames = context.getVariableNames();
        Map<String, Object> variables;
        if (variableNames == null || variableNames.isEmpty()) {
            variables = Collections.emptyMap();
        } else {
            variables = new LinkedHashMap<>(variableNames.size() + 1, 1.0f);
            for (String variableName : variableNames) {
                variables.put(variableName, context.getVariable(variableName));
            }
        }
        if (context instanceof WebContext) {
            WebContext webContext = (WebContext) context;
            return new WebEngineContext(
                    configuration, templateData, templateResolutionAttributes,
                    webContext.getRequest(),
                    webContext.getLocale(), variables);
        }
        return new EngineContext(configuration, templateData, templateResolutionAttributes,
                context.getLocale(), variables);
    }
}

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
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;

import javax.inject.Singleton;
import java.util.Map;

/**
 * Implementation of {@link org.thymeleaf.linkbuilder.ILinkBuilder} interface for {@link WebContext}
 * support.
 *
 * @author Semyon Gashchenko
 * @since 1.1.0
 */
@Singleton
public class LinkBuilder extends StandardLinkBuilder {

    @Override
    protected String computeContextPath(
            IExpressionContext context, String base, Map<String, Object> parameters) {
        if (!(context instanceof WebEngineContext)) {
            throw new TemplateProcessingException(
                    "Link base \"" + base
                            + "\" cannot be context relative (/...) unless the context " +
                            "used for executing the engine implements the " + WebEngineContext.class
                            .getName() + " interface");
        }
        HttpRequest<?> request = ((WebEngineContext) context).getRequest();
        String path = request.getPath();
        int len = path.length();
        if (len > 1 && path.charAt(len - 1) == '/') {
            return path.substring(0, len - 1);
        }
        return path;
    }

    @Override
    protected String processLink(IExpressionContext context, String link) {
        return link;
    }
}

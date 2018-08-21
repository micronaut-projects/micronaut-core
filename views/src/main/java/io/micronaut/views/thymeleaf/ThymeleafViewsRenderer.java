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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.views.ViewsConfiguration;
import io.micronaut.views.ViewsRenderer;
import io.micronaut.views.exceptions.ViewRenderingException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Map;

/**
 * Renders templates Thymeleaf Java template engine.
 *
 * @author Sergio del Amo
 * @author graemerocher
 *
 * @see <a href="https://www.thymeleaf.org">https://www.thymeleaf.org</a>
 * @since 1.0
 */
@Produces(MediaType.TEXT_HTML)
@Requires(property = ThymeleafViewsRendererConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Requires(classes = TemplateEngine.class)
@Singleton
public class ThymeleafViewsRenderer implements ViewsRenderer {

    protected final ClassLoaderTemplateResolver templateResolver;

    protected final TemplateEngine engine;

    protected ResourceLoader resourceLoader;

    /**
     * @param viewsConfiguration Views Configuration
     * @param thConfiguration    Thymeleaf template renderer configuration
     * @param resourceLoader     The resource loader
     */
    public ThymeleafViewsRenderer(ViewsConfiguration viewsConfiguration,
                                  ThymeleafViewsRendererConfiguration thConfiguration,
                                  ClassPathResourceLoader resourceLoader) {
        this.templateResolver = initializeTemplateResolver(viewsConfiguration, thConfiguration);
        this.resourceLoader = resourceLoader;
        this.engine = initializeTemplateEngine();
    }

    @Override
    public Writable render(String viewName, Object data) {
        return (writer) -> {
            final IContext context = new Context(Locale.US, variables(data));
            try {
                engine.process(viewName, context, writer);
            } catch (TemplateEngineException e) {
                throw new ViewRenderingException("Error rendering Thymeleaf view [" + viewName + "]: " + e.getMessage(), e);
            }
        };
    }

    @Override
    public boolean exists(String viewName) {
        String location = viewLocation(viewName);
        return resourceLoader.getResourceAsStream(location).isPresent();
    }

    private TemplateEngine initializeTemplateEngine() {
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(templateResolver);
        return engine;
    }

    private ClassLoaderTemplateResolver initializeTemplateResolver(ViewsConfiguration viewsConfiguration,
                                                                   ThymeleafViewsRendererConfiguration thConfiguration) {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();

        String sb = viewsConfiguration.getFolder() +
                FILE_SEPARATOR;
        templateResolver.setPrefix(sb);

        templateResolver.setCharacterEncoding(thConfiguration.getCharacterEncoding());
        templateResolver.setTemplateMode(thConfiguration.getTemplateMode());
        templateResolver.setSuffix(thConfiguration.getSuffix());
        templateResolver.setForceSuffix(thConfiguration.getForceSuffix());
        templateResolver.setForceTemplateMode(thConfiguration.getForceTemplateMode());
        templateResolver.setCacheTTLMs(thConfiguration.getCacheTTLMs());
        templateResolver.setCheckExistence(thConfiguration.getCheckExistence());
        templateResolver.setCacheable(thConfiguration.getCacheable());
        return templateResolver;
    }

    private Map<String, Object> variables(Object data) {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        } else {
            return BeanMap.of(data);
        }
    }

    private String viewLocation(final String name) {
        final StringBuilder sb = new StringBuilder();
        if (templateResolver.getPrefix() != null) {
            sb.append(templateResolver.getPrefix());
            sb.append(FILE_SEPARATOR);
        }
        sb.append(name.replace("/", FILE_SEPARATOR));
        if (templateResolver.getSuffix() != null) {
            sb.append(templateResolver.getSuffix());
        }
        return sb.toString();
    }
}

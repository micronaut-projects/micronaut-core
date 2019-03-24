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

import io.micronaut.context.annotation.Factory;
import io.micronaut.views.ViewsConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IEngineContextFactory;
import org.thymeleaf.linkbuilder.ILinkBuilder;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import javax.inject.Singleton;

/**
 * A factory for Thymeleaf beans.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
@Factory
public class ThymeleafFactory {

    /**
     * Constructs the template resolver bean.
     *
     * @param viewsConfiguration The views configuration
     * @param rendererConfiguration The renderer configuration
     * @return The template resolver
     */
    @Singleton
    public AbstractConfigurableTemplateResolver templateResolver(ViewsConfiguration viewsConfiguration,
                                                          ThymeleafViewsRendererConfiguration rendererConfiguration) {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();

        templateResolver.setPrefix(viewsConfiguration.getFolder());
        templateResolver.setCharacterEncoding(rendererConfiguration.getCharacterEncoding());
        templateResolver.setTemplateMode(rendererConfiguration.getTemplateMode());
        templateResolver.setSuffix(rendererConfiguration.getSuffix());
        templateResolver.setForceSuffix(rendererConfiguration.getForceSuffix());
        templateResolver.setForceTemplateMode(rendererConfiguration.getForceTemplateMode());
        templateResolver.setCacheTTLMs(rendererConfiguration.getCacheTTLMs());
        templateResolver.setCheckExistence(rendererConfiguration.getCheckExistence());
        templateResolver.setCacheable(rendererConfiguration.getCacheable());

        return templateResolver;
    }

    /**
     * Constructs the template engine.
     *
     * @param templateResolver The template resolver
     * @param engineContextFactory The engine context factory
     * @param linkBuilder The link builder
     * @return The template engine
     */
    @Singleton
    public TemplateEngine templateEngine(ITemplateResolver templateResolver,
            IEngineContextFactory engineContextFactory, ILinkBuilder linkBuilder) {
        TemplateEngine engine = new TemplateEngine();
        engine.setEngineContextFactory(engineContextFactory);
        engine.setLinkBuilder(linkBuilder);
        engine.setTemplateResolver(templateResolver);
        return engine;
    }
}

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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import javax.inject.Singleton;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Renders templates Thymeleaf Java template engine.
 * @see <a href="https://www.thymeleaf.org">https://www.thymeleaf.org</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = ThymeleafTemplateRendererConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Requires(classes = TemplateEngine.class)
@Singleton
public class ThymeleafTemplateRenderer implements TemplateRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(ThymeleafTemplateRenderer.class);

    protected final TemplateEngine engine;

    /**
     * @param templatesConfiguration Templates Configuration
     * @param thConfiguration Thymeleaf template renderer configuration
     */
    public ThymeleafTemplateRenderer(TemplatesConfiguration templatesConfiguration,
                                     ThymeleafTemplateRendererConfiguration thConfiguration) {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();

        StringBuilder sb = new StringBuilder();
        sb.append(templatesConfiguration.getFolder());
        sb.append("/");
        templateResolver.setPrefix(sb.toString());

        templateResolver.setCharacterEncoding(thConfiguration.getCharacterEncoding());
        templateResolver.setTemplateMode(thConfiguration.getTemplateMode());
        templateResolver.setSuffix(thConfiguration.getSuffix());
        templateResolver.setForceSuffix(thConfiguration.getForceSuffix());
        templateResolver.setForceTemplateMode(thConfiguration.getForceTemplateMode());
        templateResolver.setCacheTTLMs(thConfiguration.getCacheTTLMs());
        templateResolver.setCheckExistence(thConfiguration.getCheckExistence());
        templateResolver.setCacheable(thConfiguration.getCacheable());

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(templateResolver);
        this.engine = engine;
    }

    @Override
    public Optional<String> render(String view, Object data) {
        StringWriter writer = new StringWriter();
        final IContext context = new Context(Locale.US, variables(data));
        try {
            engine.process(view, context, writer);
            return Optional.of(writer.toString());
        } catch (TemplateEngineException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getMessage());
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> variables(Object data) {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return BeanMap.of(data);
    }
}

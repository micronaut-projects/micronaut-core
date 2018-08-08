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

import javax.inject.Singleton;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanMap;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders with templates with Apache Velocity Project.
 * @see <a href="http://velocity.apache.org">http://velocity.apache.org</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = VelocityTemplateRendererConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Requires(classes = VelocityEngine.class)
@Singleton
public class VelocityTemplateRenderer implements TemplateRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(VelocityTemplateRenderer.class);

    protected final VelocityEngine velocityEngine;
    protected final TemplatesConfiguration templatesConfiguration;

    /**
     *
     * @param templatesConfiguration Views Configuration
     */
    VelocityTemplateRenderer(TemplatesConfiguration templatesConfiguration) {
        this.templatesConfiguration = templatesConfiguration;

        final Properties p = new Properties();
        p.setProperty("resource.loader", "class");
        p.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine = new VelocityEngine(p);
    }

    @Override
    public Optional<String> render(String view, Object data) {
        final StringWriter writer = new StringWriter();
        Optional<String> result = Optional.empty();

        Map<String, Object> context = context(data);
        final VelocityContext velocityContext = new VelocityContext(context);
        String templateName = templateName(view);
        try {
            velocityEngine.mergeTemplate(templateName, StandardCharsets.UTF_8.name(), velocityContext, writer);
            result = Optional.of(writer.toString());
        } catch (ResourceNotFoundException | ParseErrorException | MethodInvocationException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getMessage());
            }
        }
        return result;
    }

    private Map<String, Object> context(Object data) {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return BeanMap.of(data);
    }

    private String templateName(final String name) {
        final StringBuilder sb = new StringBuilder();
        sb.append(templatesConfiguration.getFolder());
        sb.append("/");
        sb.append(name);
        if (!name.endsWith(".vm")) {
            sb.append(".vm");
        }
        return sb.toString();
    }
}

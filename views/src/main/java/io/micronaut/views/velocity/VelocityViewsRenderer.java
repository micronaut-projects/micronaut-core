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

package io.micronaut.views.velocity;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.io.Writable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.views.ViewsConfiguration;
import io.micronaut.views.ViewsRenderer;
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
@Produces(MediaType.TEXT_HTML)
@Requires(property = VelocityViewsRendererConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Requires(classes = VelocityEngine.class)
@Singleton
public class VelocityViewsRenderer implements ViewsRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(VelocityViewsRenderer.class);

    protected final VelocityEngine velocityEngine;
    protected final ViewsConfiguration viewsConfiguration;
    protected final VelocityViewsRendererConfiguration velocityConfiguration;

    /**
     * @param viewsConfiguration Views Configuration
     * @param velocityConfiguration Velocity Configuration
     */
    VelocityViewsRenderer(ViewsConfiguration viewsConfiguration,
                          VelocityViewsRendererConfiguration velocityConfiguration) {
        this.viewsConfiguration = viewsConfiguration;
        this.velocityConfiguration = velocityConfiguration;
        this.velocityEngine = initializeVelocityEngine();
    }

    private VelocityEngine initializeVelocityEngine() {
        final Properties p = new Properties();
        p.setProperty("resource.loader", "class");
        p.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        return new VelocityEngine(p);
    }

    @Override
    public Writable render(String view, Object data) {
        return (writer) -> {
            Map<String, Object> context = context(data);
            final VelocityContext velocityContext = new VelocityContext(context);
            String viewName = viewName(view);
            try {
                velocityEngine.mergeTemplate(viewName, StandardCharsets.UTF_8.name(), velocityContext, writer);
            } catch (ResourceNotFoundException | ParseErrorException | MethodInvocationException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error(e.getMessage());
                }
            }
        };
    }

    private Map<String, Object> context(Object data) {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return BeanMap.of(data);
    }

    private String viewName(final String name) {
        final StringBuilder sb = new StringBuilder();
        if (viewsConfiguration.getFolder() != null) {
            sb.append(viewsConfiguration.getFolder());
            sb.append(FILE_SEPARATOR);
        }
        sb.append(name);
        final String extension = extension();
        if (!name.endsWith(extension)) {
            sb.append(extension);
        }
        return sb.toString();
    }

    private String extension() {
        StringBuilder sb = new StringBuilder();
        sb.append(EXTENSION_SEPARATOR);
        sb.append(velocityConfiguration.getDefaultExtension());
        return sb.toString();
    }

    @Override
    public boolean exists(String viewName) {
        try {
            velocityEngine.getTemplate(viewName(viewName));
        } catch (ResourceNotFoundException | ParseErrorException e) {
            return false;
        }
        return true;
    }
}

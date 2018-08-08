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

package io.micronaut.views.handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.views.ViewsConfiguration;
import io.micronaut.views.ViewsRenderer;
import io.micronaut.views.exceptions.ViewRenderingException;
import javax.inject.Singleton;

/**
 * Renders Views with with Handlebars.java.
 *
 * @author Sergio del Amo
 * @see <a href="http://jknack.github.io/handlebars.java/">http://jknack.github.io/handlebars.java/</a>
 * @since 1.0
 */
@Produces(MediaType.TEXT_HTML)
@Requires(property = HandlebarsViewsRendererConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Requires(classes = Handlebars.class)
@Singleton
public class HandlebarsViewsRenderer implements ViewsRenderer {

    protected final ViewsConfiguration viewsConfiguration;

    protected final ResourceLoader resourceLoader;

    protected HandlebarsViewsRendererConfiguration handlebarsViewsRendererConfiguration;

    protected Handlebars handlebars = new Handlebars();


    /**
     * @param viewsConfiguration                   Views Configuration.
     * @param resourceLoader                       Resource Loader
     * @param handlebarsViewsRendererConfiguration Handlebars ViewRenderer Configuration.
     */
    public HandlebarsViewsRenderer(ViewsConfiguration viewsConfiguration,
                                   ClassPathResourceLoader resourceLoader,
                                   HandlebarsViewsRendererConfiguration handlebarsViewsRendererConfiguration) {
        this.viewsConfiguration = viewsConfiguration;
        this.resourceLoader = resourceLoader;
        this.handlebarsViewsRendererConfiguration = handlebarsViewsRendererConfiguration;
    }

    @Override
    public Writable render(String viewName, Object data) {
        return (writer) -> {
            String location = viewLocation(viewName);
            try {
                Template template = handlebars.compile(location);
                template.apply(data, writer);
            } catch (Throwable e) {
                throw new ViewRenderingException("Error rendering Handlebars view [" + viewName + "]: " + e.getMessage(), e);
            }
        };
    }

    @Override
    public boolean exists(String viewName) {
        String location = viewLocation(viewName);
        StringBuilder sb = new StringBuilder(location);
        final String extension = extension();
        if (!location.endsWith(extension)) {
            sb.append(extension);
        }
        return resourceLoader.getResourceAsStream(sb.toString()).isPresent();
    }

    private String viewLocation(final String name) {
        final StringBuilder sb = new StringBuilder();
        if (viewsConfiguration.getFolder() != null) {
            sb.append(viewsConfiguration.getFolder());
        }
        sb.append(FILE_SEPARATOR);
        sb.append(name.replace("/", FILE_SEPARATOR));
        int index = sb.indexOf(extension());
        if (index != -1) {
            return sb.substring(0, index);
        }
        return sb.toString();
    }

    private String extension() {
        return EXTENSION_SEPARATOR +
                handlebarsViewsRendererConfiguration.getDefaultExtension();
    }

}

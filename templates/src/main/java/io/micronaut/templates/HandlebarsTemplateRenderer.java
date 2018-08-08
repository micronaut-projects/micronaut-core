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

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.micronaut.context.annotation.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Renders Templates with with Handlebars.java.
 * @see <a href="http://jknack.github.io/handlebars.java/">http://jknack.github.io/handlebars.java/</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = HandlebarsTemplateRendererConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Requires(classes = Handlebars.class)
@Singleton
public class HandlebarsTemplateRenderer implements TemplateRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(HandlebarsTemplateRenderer.class);

    protected final TemplatesConfiguration templatesConfiguration;

    private Handlebars handlebars = new Handlebars();

    /**
     *
     * @param templatesConfiguration Templates Configuration.
     */
    public HandlebarsTemplateRenderer(TemplatesConfiguration templatesConfiguration) {
        this.templatesConfiguration = templatesConfiguration;
    }

    @Override
    public Optional<String> render(String view, Object data) {
        Optional<String> result = Optional.empty();
        String location = templateLocation(view);
        try {
            StringWriter writer = new StringWriter();
            Template template = handlebars.compile(location);
            template.apply(data, writer);
            result = Optional.of(writer.toString());
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getMessage());
            }
        }
        return result;
    }

    private String templateLocation(final String name) {
        final StringBuilder sb = new StringBuilder();
        sb.append(templatesConfiguration.getFolder());
        sb.append("/");
        sb.append(name);
        return sb.toString();
    }
}

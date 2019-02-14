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

package io.micronaut.views.freemarker;

import freemarker.template.Configuration;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.views.ViewsConfiguration;

import javax.inject.Singleton;

/**
 * Factory for freemarker beans.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
@Factory
public class FreemarkerFactory {

    /**
     * Constructs a freemarker configuration.
     *
     * @param freemarkerConfiguration The freemarker configuration properties
     * @param viewsConfiguration The views configuration
     * @param environment The environment
     * @return The freemarker configuration
     */
    @Singleton
    Configuration getConfiguration(FreemarkerViewsRendererConfiguration freemarkerConfiguration,
                                   ViewsConfiguration viewsConfiguration,
                                   Environment environment) {
        Configuration configuration = new Configuration(freemarkerConfiguration.getIncompatibleImprovements());
        configuration.setClassLoaderForTemplateLoading(environment.getClassLoader(), "/" + viewsConfiguration.getFolder());
        return configuration;
    }
}

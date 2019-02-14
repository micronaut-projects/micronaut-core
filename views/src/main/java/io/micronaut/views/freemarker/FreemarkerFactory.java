package io.micronaut.views.freemarker;

import freemarker.template.Configuration;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.views.ViewsConfiguration;

import javax.inject.Singleton;

@Factory
public class FreemarkerFactory {

    @Singleton
    Configuration getConfiguration(FreemarkerViewsRendererConfiguration freemarkerConfiguration,
                                   ViewsConfiguration viewsConfiguration,
                                   Environment environment) {
        Configuration configuration = new Configuration(freemarkerConfiguration.getIncompatibleImprovements());
        configuration.setClassLoaderForTemplateLoading(environment.getClassLoader(), "/" + viewsConfiguration.getFolder());
        return configuration;
    }
}

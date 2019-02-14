package io.micronaut.views.thymeleaf;

import io.micronaut.context.annotation.Factory;
import io.micronaut.views.ViewsConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import javax.inject.Singleton;

@Factory
public class ThymeleafFactory {

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

    @Singleton
    public TemplateEngine templateEngine(ITemplateResolver templateResolver) {
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(templateResolver);
        return engine;
    }
}

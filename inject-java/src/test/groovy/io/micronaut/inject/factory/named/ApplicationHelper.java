package io.micronaut.inject.factory.named;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class ApplicationHelper {
    @Inject
    @Named("ias-test-template")
    Provider<Template> templateProvider;

    public Template getTemplate() {
        return templateProvider.get();
    }
}

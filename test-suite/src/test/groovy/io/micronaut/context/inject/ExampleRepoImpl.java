package io.micronaut.context.inject;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExampleRepoImpl implements ExampleRepo {

    @Inject
    EventManager eventManager;

    @PostConstruct
    public void init() {
        eventManager.register("test");
    }

    @Override
    public String find() {
        return null;
    }


}

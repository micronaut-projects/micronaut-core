package io.micronaut.inject.scope.custom;

import javax.annotation.PreDestroy;

@SpecialBean
public class TestService {

    public boolean destroyed;

    @PreDestroy
    void destroyMe() {
        destroyed = true;
    }
}

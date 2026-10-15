package io.micronaut.inject.destroydependent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@SharedScope
public class SharedBean {

    static int created;
    static int destroyed;

    private int id;

    @PostConstruct
    void init() {
        id = ++created;
    }

    public int id() {
        return id;
    }

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}

package io.micronaut.inject.context;

import io.micronaut.inject.annotation.ScopeOne;

import javax.inject.Singleton;

@Singleton
@ScopeOne
public class EagerInitBean {
    static boolean created = false;

    EagerInitBean() {
        created = true;
    }
}

package io.micronaut.inject.failures.nesteddependency;

import javax.inject.Singleton;

@Singleton
public class C {
    public C(D d) {

    }
}

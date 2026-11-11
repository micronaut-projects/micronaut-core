package io.micronaut.inject.registration;

import jakarta.inject.Singleton;

@Singleton
public class SingletonService {

    static int created;

    public SingletonService() {
        created++;
    }
}

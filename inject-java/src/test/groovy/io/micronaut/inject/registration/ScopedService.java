package io.micronaut.inject.registration;

@RegistrationScope
public class ScopedService {

    static int created;

    public ScopedService() {
        created++;
    }
}

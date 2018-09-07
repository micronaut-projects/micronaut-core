package io.micronaut.security.atsecured

import io.micronaut.context.annotation.Requires
import io.micronaut.security.Secured
import javax.inject.Singleton

@Requires(property = 'spec.name', value = 'AtSecuredAppliedToServiceMethodSpec')
@Singleton
class BookRepository {

    @Secured("ROLE_DETECTIVE")
    List<String> findAll() {
        ['Building Microservice', 'Release it']
    }
}

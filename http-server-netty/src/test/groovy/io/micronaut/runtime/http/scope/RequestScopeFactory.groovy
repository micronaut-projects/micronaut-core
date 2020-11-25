package io.micronaut.runtime.http.scope

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory

@Factory
class RequestScopeFactory {

    @RequestScope
    @Bean(preDestroy = "killMe")
    RequestScopeFactoryBean bean() {
        return new RequestScopeFactoryBean()
    }
}

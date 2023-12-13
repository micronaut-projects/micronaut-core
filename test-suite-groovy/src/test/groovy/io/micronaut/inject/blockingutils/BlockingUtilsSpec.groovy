package io.micronaut.inject.blockingutils

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Blocking
import io.micronaut.inject.BlockingUtils
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@Property(name = "spec.name", value = "BlockingUtilsSpec")
@MicronautTest(startApplication = false)
class BlockingUtilsSpec extends Specification {

    @Inject
    BeanContext beanContext

    void "#clazz authenticate method is #description"(boolean isBlocking,
                                                      Class<? extends AuthenticationProvider> clazz,
                                                      String description) {
        expect:
        isBlocking == BlockingUtils.isMethodBlocking(beanContext, beanContext.getBean(clazz), "authenticate", String.class, String.class)

        where:
        isBlocking | clazz
        true       | BlockingAuthenticationProvider.class
        false      | NonBlockingAuthenticationProvider.class
        description = isBlocking ? "is annotated with @Blocking" : "is not annotated with @Blocking"
    }

    @Requires(property = "spec.name", value = "BlockingUtilsSpec")
    @Singleton
    static class BlockingAuthenticationProvider implements AuthenticationProvider {
        @Override
        @Blocking
        boolean authenticate(String username, String password) {
            return false
        }
    }

    @Requires(property = "spec.name", value = "BlockingUtilsSpec")
    @Singleton
    static class NonBlockingAuthenticationProvider implements AuthenticationProvider {
        @Override
        boolean authenticate(String username, String password) {
            return false
        }
    }

}

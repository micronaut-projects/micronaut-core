package io.micronaut.crac

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.crac.support.CracContext
import io.micronaut.crac.support.GlobalCracContextFactory
import io.micronaut.crac.support.OrderedCracResource
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CracRegistrationSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': CracRegistrationSpec.simpleName
    ], Environment.TEST)

    @Shared
    def context = embeddedServer.applicationContext

    static registrations = []

    def "resources are registered in the expected order"() {
        expect:
        registrations == [
                'TestResource',
                'NettyEmbeddedServerCracHander',
                'TestResource3',
                'TestResource2',
        ]
    }

    @Factory
    @Requires(property = "spec.name", value = "CracRegistrationSpec")
    static class ContextFactory {

        @Singleton
        @Replaces(bean = CracContext, factory = GlobalCracContextFactory)
        CracContext cracContext() {
            new CracContext() {

                @Override
                void register(OrderedCracResource orderedCracResource) {
                    registrations.add(orderedCracResource.class.simpleName)
                }
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "CracRegistrationSpec")
    static class TestResource implements OrderedCracResource {

        @Override
        int getOrder() {
            -1
        }

        @Override
        void beforeCheckpoint(CracContext context) throws Exception {

        }

        @Override
        void afterRestore(CracContext context) throws Exception {

        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "CracRegistrationSpec")
    static class TestResource2 implements OrderedCracResource {

        @Override
        int getOrder() {
            3
        }

        @Override
        void beforeCheckpoint(CracContext context) throws Exception {

        }

        @Override
        void afterRestore(CracContext context) throws Exception {

        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "CracRegistrationSpec")
    static class TestResource3 implements OrderedCracResource {

        @Override
        int getOrder() {
            2
        }

        @Override
        void beforeCheckpoint(CracContext context) throws Exception {

        }

        @Override
        void afterRestore(CracContext context) throws Exception {

        }
    }
}

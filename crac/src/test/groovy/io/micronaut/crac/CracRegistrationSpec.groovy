package io.micronaut.crac

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.crac.support.CracContext
import io.micronaut.crac.support.OrderedCracResource
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@Property(name = "spec.name", value = "CracRegistrationSpec")
@MicronautTest
class CracRegistrationSpec extends Specification {

    @Inject
    CracContextReplacement cracContextReplacement

    def "resources are registered in the expected order"() {
        expect:
        cracContextReplacement.registrations == [
                'TestResource',
                'NettyEmbeddedServerCracHander',
                'TestResource3',
                'TestResource2',
        ]
    }

    @Singleton
    @Requires(property = "spec.name", value = "CracRegistrationSpec")
    @Replaces(CracContext.class)
    static class CracContextReplacement implements CracContext {
        static List<String> registrations = []

        @Override
        void register(@NonNull OrderedCracResource orderedCracResource) {
            registrations.add(orderedCracResource.class.simpleName)
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
        void beforeCheckpoint(@NonNull CracContext context) throws Exception {

        }

        @Override
        void afterRestore(@NonNull CracContext context) throws Exception {

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
        void beforeCheckpoint(@NonNull CracContext context) throws Exception {

        }

        @Override
        void afterRestore(@NonNull CracContext context) throws Exception {

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
        void beforeCheckpoint(@NonNull CracContext context) throws Exception {

        }

        @Override
        void afterRestore(@NonNull CracContext context) throws Exception {

        }
    }
}

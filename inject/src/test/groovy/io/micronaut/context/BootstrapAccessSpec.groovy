package io.micronaut.context

import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.BootstrapPropertySourceLocator
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.context.exceptions.NoSuchBeanException
import jakarta.inject.Singleton
import spock.lang.Specification

class BootstrapAccessSpec extends Specification {
    def test() {
        given:
        def ctx = ApplicationContext.builder()
                .properties([
                        'spec.name': 'BootstrapAccessSpec'
                ])
                .bootstrapEnvironment(true)
                .start()

        expect:
        ctx.getBean(AccessBean).communicationObject.data == "foo"

        cleanup:
        ctx.close()
    }

    @BootstrapContextCompatible
    @Singleton
    @Requires(property = "spec.name", value = "BootstrapAccessSpec")
    static class BootBean implements BootstrapPropertySourceLocator {
        BootBean(BeanContext context, BootstrapContextAccess access) {
            try {
                context.getBean(NormalBean)
                throw new IllegalStateException("Not in boot context?")
            } catch (NoSuchBeanException expected) {
                // should happen in boot context
            }
            access.mainRegistry.registerSingleton(new CommunicationObject("foo"))
        }

        @Override
        Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException {
            return List.of()
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "BootstrapAccessSpec")
    static class NormalBean {
    }

    @Singleton
    @Requires(property = "spec.name", value = "BootstrapAccessSpec")
    static class AccessBean {
        CommunicationObject communicationObject

        AccessBean(CommunicationObject communicationObject) {
            this.communicationObject = communicationObject
        }
    }

    static class CommunicationObject {
        final String data

        CommunicationObject(String data) {
            this.data = data
        }
    }
}

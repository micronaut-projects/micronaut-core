package io.micronaut.http.client.bootstrap

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.BootstrapPropertySourceLocator
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.util.StringUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import javax.inject.Singleton

class ClientBootstrapSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": getClass().simpleName + "Server"])

    @RestoreSystemProperties
    void "test the client is available in the bootstrap context"() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, StringUtils.TRUE)
        ApplicationContext beanContext = ApplicationContext.run(["server-uri": server.getURL().toString(), "spec.name": getClass().simpleName])

        expect:
        beanContext.getRequiredProperty("bootstrap-call", String) == "Hello Bootstrap"

        cleanup:
        beanContext.close()
    }

    @Requires(property = "spec.name", value = "ClientBootstrapSpecServer")
    @Controller
    static class MyController {

        @Get("/bootstrap")
        String call() {
            "Hello Bootstrap"
        }
    }

    @Requires(property = "spec.name", value = "ClientBootstrapSpec")
    @Client("\${server-uri}")
    @BootstrapContextCompatible
    static interface MyClient {

        @Get("/bootstrap")
        String call()
    }

    @Requires(property = "spec.name", value = "ClientBootstrapSpec")
    @Singleton
    @BootstrapContextCompatible
    static class MyConfigurationClient implements BootstrapPropertySourceLocator {

        private final MyClient myClient

        MyConfigurationClient(MyClient myClient) {
            this.myClient = myClient
        }

        @Override
        Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException {
            return [PropertySource.of(Collections.singletonMap("bootstrap-call", myClient.call()))]
        }
    }
}

package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.ExecutableMethod
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.management.endpoint.annotation.Sensitive
import spock.lang.Specification

class EndpointSensitivitySpec extends Specification {

    void "test endpoints are sensitive by default"() {
        ApplicationContext applicationContext = ApplicationContext.run(["endpoint.test": "default-sensitive"])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(DefaultSensitive, "go").get()

        then:
        processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    void "test endpoint sensitivity can be changed with config"() {
        ApplicationContext applicationContext = ApplicationContext.run(["endpoint.test": "default-sensitive", "endpoints.default-sensitive.sensitive": false])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(DefaultSensitive, "go").get()

        then:
        !processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    void "test endpoints are not sensitive with defaultSensitive=false"() {
        ApplicationContext applicationContext = ApplicationContext.run(["endpoint.test": "default-sensitive-value"])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(DefaultSensitiveValue, "go").get()

        then:
        !processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    void "test the sensitive annotation has precedence over the default sensitivity"() {
        ApplicationContext applicationContext = ApplicationContext.run(["endpoint.test": "method-sensitive"])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(MethodSensitive, "go").get()

        then:
        !processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    void "test the sensitive annotation property relies on the default value"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                "endpoint.test": "method-sensitive-config"
        ])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(MethodSensitiveConfig, "go").get()

        then:
        processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    void "test the sensitive annotation property uses the value from config"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                "endpoint.test": "method-sensitive-config",
                "endpoints.default-sensitive.go-sensitive": false
        ])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(MethodSensitiveConfig, "go").get()

        then:
        !processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    void "test the sensitive annotation property uses the value from config with custom prefix"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                "endpoint.test": "method-sensitive-custom-prefix",
                "myapp.default-sensitive.go-sensitive": false
        ])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(MethodSensitivePrefix, "go").get()

        then:
        !processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    void "test the sensitive annotation default has precedence over the endpoint annotation default"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                "endpoint.test": "method-sensitive-config-default"
        ])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(MethodSensitiveConfigDefault, "go").get()

        then:
        !processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    void "test the sensitive annotation with no members is equivalent to @Sensitive(true)"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                "endpoint.test": "method-sensitive-default"
        ])
        EndpointSensitivityProcessor processor = applicationContext.getBean(EndpointSensitivityProcessor)

        when:
        ExecutableMethod<?, ?> method = applicationContext.findExecutableMethod(MethodSensitiveDefault, "go").get()

        then:
        processor.getEndpointMethods().get(method)

        cleanup:
        applicationContext.close()
    }

    @Requires(property = "endpoint.test", value = "default-sensitive")
    @Endpoint("default-sensitive")
    static class DefaultSensitive {

        @Read
        String go() {
            return "ok"
        }
    }

    @Requires(property = "endpoint.test", value = "default-sensitive-value")
    @Endpoint(id = "default-sensitive", defaultSensitive = false)
    static class DefaultSensitiveValue {

        @Read
        String go() {
            return "ok"
        }
    }

    @Requires(property = "endpoint.test", value = "method-sensitive")
    @Endpoint("default-sensitive")
    static class MethodSensitive {

        @Read
        @Sensitive(false)
        String go() {
            return "ok"
        }
    }

    @Requires(property = "endpoint.test", value = "method-sensitive-config")
    @Endpoint("default-sensitive")
    static class MethodSensitiveConfig {

        @Read
        @Sensitive(property = "go-sensitive")
        String go() {
            return "ok"
        }
    }

    @Requires(property = "endpoint.test", value = "method-sensitive-custom-prefix")
    @Endpoint(id = "default-sensitive", defaultSensitive = false, prefix = "myapp")
    static class MethodSensitivePrefix {

        @Read
        @Sensitive(property = "go-sensitive")
        String go() {
            return "ok"
        }
    }

    @Requires(property = "endpoint.test", value = "method-sensitive-config-default")
    @Endpoint("default-sensitive")
    static class MethodSensitiveConfigDefault {

        @Read
        @Sensitive(property = "go-sensitive", defaultValue = false)
        String go() {
            return "ok"
        }
    }

    @Requires(property = "endpoint.test", value = "method-sensitive-default")
    @Endpoint(id = "default-sensitive", defaultSensitive = false)
    static class MethodSensitiveDefault {

        @Read
        @Sensitive
        String go() {
            return "ok"
        }
    }

}

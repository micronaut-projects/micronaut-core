package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.watch.ConfigurationWatcher
import io.micronaut.inject.ExecutableMethod
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher
import spock.lang.Specification

class EndpointSensitivityWatchSpec extends Specification {

    void "an endpoint's sensitivity follows the configuration, and an endpoint that went leaves the map"() {
        given:
        Map<String, Object> values = ["endpoint.test": "default-sensitive"]
        def context = ApplicationContext.run(values)
        def processor = context.getBean(EndpointSensitivityProcessor)
        ExecutableMethod<?, ?> method = context.findExecutableMethod(EndpointSensitivitySpec.DefaultSensitive, "go").get()

        expect: "sensitive by default, and the processor is not adapted: it watches"
        processor.endpointMethods.get(method)
        !((DefaultBeanContext) context).adaptedProcessors().contains(processor)

        when: "the endpoint is made public in the configuration and the configuration is refreshed"
        values["endpoints.default-sensitive.sensitive"] = false
        def result = context.getBean(ConfigurationRefresher).refresh()

        then:
        result.outcomes().contains(ConfigurationWatcher.Outcome.APPLIED)
        !processor.endpointMethods.get(method)

        when: "the setting goes again"
        values.remove("endpoints.default-sensitive.sensitive")
        context.getBean(ConfigurationRefresher).refresh()

        then: "the endpoint is sensitive again, the destroyed entry not consulted"
        processor.endpointMethods.get(method)

        when: "the endpoint goes"
        def definition = context.getBeanDefinition(EndpointSensitivitySpec.DefaultSensitive)
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [])

        then:
        !processor.endpointMethods.containsKey(method)

        cleanup:
        context.close()
    }

    void "a sensitivity read under an endpoint's own prefix follows that prefix"() {
        given:
        Map<String, Object> values = ["endpoint.test": "method-sensitive-custom-prefix", "myapp.default-sensitive.go-sensitive": true]
        def context = ApplicationContext.run(values)
        def processor = context.getBean(EndpointSensitivityProcessor)
        ExecutableMethod<?, ?> method = context.findExecutableMethod(EndpointSensitivitySpec.MethodSensitivePrefix, "go").get()

        expect:
        processor.endpointMethods.get(method)

        when: "the setting under the custom prefix changes"
        values["myapp.default-sensitive.go-sensitive"] = false
        def result = context.getBean(ConfigurationRefresher).refresh()

        then:
        result.outcomes().contains(ConfigurationWatcher.Outcome.APPLIED)
        !processor.endpointMethods.get(method)

        cleanup:
        context.close()
    }
}

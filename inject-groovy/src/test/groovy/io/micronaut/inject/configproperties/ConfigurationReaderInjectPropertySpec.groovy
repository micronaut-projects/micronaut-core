package io.micronaut.inject.configproperties

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext

class ConfigurationReaderInjectPropertySpec extends AbstractBeanDefinitionSpec {

    void "test @Inject on a Groovy property of a configuration reader injects the bean"() {
        given:
        ApplicationContext ctx = buildContext('''
package injprop1

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.inject.Inject
import jakarta.inject.Singleton

@ConfigurationProperties("heartbeat")
class HeartbeatConfig {

    @Inject
    HeartbeatService heartbeatService

    String name
}

@Singleton
class HeartbeatService {
    String beat() { "ok" }
}
''', false, ["heartbeat.name": "foo"])

        when:
        def config = getBean(ctx, 'injprop1.HeartbeatConfig')

        then:
        config.name == "foo"
        config.heartbeatService != null
        config.heartbeatService.beat() == "ok"

        cleanup:
        ctx.close()
    }

    void "test @Inject on a Groovy property of an endpoint-like configuration reader injects the bean"() {
        given:
        ApplicationContext ctx = buildContext('''
package injprop2

import io.micronaut.context.annotation.Executable
import io.micronaut.inject.configproperties.TestEndpoint4
import jakarta.inject.Inject
import jakarta.inject.Singleton

@TestEndpoint4("heartbeat")
class HeartbeatEndpoint {

    @Inject
    HeartbeatService heartbeatService

    boolean sensitive

    @Executable
    String heartbeat() {
        heartbeatService.beat()
    }
}

@Singleton
class HeartbeatService {
    String beat() { "ok" }
}
''', false, ["endpoints.heartbeat.sensitive": "true"])

        when:
        def endpoint = getBean(ctx, 'injprop2.HeartbeatEndpoint')

        then:
        endpoint.sensitive
        endpoint.heartbeatService != null
        endpoint.heartbeat() == "ok"

        cleanup:
        ctx.close()
    }
}

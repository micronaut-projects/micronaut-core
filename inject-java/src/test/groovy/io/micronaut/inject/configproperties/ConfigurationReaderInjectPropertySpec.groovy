package io.micronaut.inject.configproperties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class ConfigurationReaderInjectPropertySpec extends AbstractTypeElementSpec {

    void "test @Inject on a field with accessors of a configuration reader injects the bean"() {
        given:
        ApplicationContext ctx = buildContext('injprop1.HeartbeatConfig', '''
package injprop1;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@ConfigurationProperties("heartbeat")
class HeartbeatConfig {

    @Inject
    private HeartbeatService heartbeatService;

    private String name;

    public HeartbeatService getHeartbeatService() {
        return heartbeatService;
    }

    public void setHeartbeatService(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

@Singleton
class HeartbeatService {
    String beat() {
        return "ok";
    }
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
}

package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.watch.ConfigurationChange
import io.micronaut.context.watch.ConfigurationWatcher
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class NettyServerConfigurationWatchSpec extends Specification {

    void "the server applies a pipeline change in place and asks for a restart when its address changes"() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ["spec.name": "NettyServerConfigurationWatchSpec"])
        def context = (DefaultBeanContext) server.applicationContext

        expect:
        server.running

        when: "a timeout changes"
        def outcomes = context.notifyConfigurationChange(ConfigurationChange.ofKeys(["micronaut.server.read-timeout"] as Set))

        then:
        outcomes == [ConfigurationWatcher.Outcome.APPLIED]
        server.running

        when: "the port changes"
        outcomes = context.notifyConfigurationChange(ConfigurationChange.ofKeys(["micronaut.server.port"] as Set))

        then:
        outcomes == [ConfigurationWatcher.Outcome.REQUIRES_RESTART]

        when: "an explicit listener changes"
        outcomes = context.notifyConfigurationChange(ConfigurationChange.ofKeys(["micronaut.server.netty.listeners.main.port"] as Set))

        then:
        outcomes == [ConfigurationWatcher.Outcome.REQUIRES_RESTART]

        when: "a bootstrap option changes"
        outcomes = context.notifyConfigurationChange(ConfigurationChange.ofKeys(["micronaut.server.netty.child-options.SO_KEEPALIVE"] as Set))

        then:
        outcomes == [ConfigurationWatcher.Outcome.REQUIRES_RESTART]

        when: "an ssl setting changes"
        outcomes = context.notifyConfigurationChange(ConfigurationChange.ofKeys(["micronaut.ssl.key-store.path"] as Set))

        then:
        outcomes == [ConfigurationWatcher.Outcome.APPLIED]

        when: "the server stops"
        server.stop()

        then: "its watches are gone"
        context.notifyConfigurationChange(ConfigurationChange.ofKeys(["micronaut.server.port"] as Set)).isEmpty()

        cleanup:
        server.close()
    }
}

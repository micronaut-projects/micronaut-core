package io.micronaut.configuration.metrics.micrometer.graphite

import io.micrometer.graphite.GraphiteConfig
import spock.lang.Specification

class GraphiteConfigAdapterSpec extends Specification {

    GraphiteConfig graphiteConfig
    GraphiteConfigAdapter adapter
    GraphiteConfigurationProperties props

    void setup() {
        graphiteConfig = new GraphiteConfig() {
            String get(String key) { return null }
        }

        props = new GraphiteConfigurationProperties()
        props.setPort(graphiteConfig.port() + 1)

        adapter = new GraphiteConfigAdapter(props)
    }

    void "verify property override of default grapics config works"() {
        when:
            int newPort = adapter.port()

        then:
            newPort == props.port
    }

    void "verify graphite config property override used when no property specified in GraphiteConfigurationProperties"() {
        when:
            props.setPort(null)
            int newPort = adapter.port()

        then:
            newPort == graphiteConfig.port()
    }

}

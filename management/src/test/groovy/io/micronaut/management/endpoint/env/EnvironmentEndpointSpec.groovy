package io.micronaut.management.endpoint.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification

class EnvironmentEndpointSpec extends Specification {

    @AutoCleanup
    private EmbeddedServer embeddedServer

    @AutoCleanup
    private HttpClient client

    void "the env endpoint is disabled by default"() {
        given:
        this.embeddedServer = ApplicationContext.run(EmbeddedServer, Environment.TEST)
        this.client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        call("/${EnvironmentEndpoint.NAME}")

        then:
        HttpClientResponseException responseException = thrown()
        responseException.status.code == 404
    }

    void "the env endpoint is sensitive by default"() {
        given:
        this.embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.env.enabled': true], Environment.TEST)
        this.client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        call("/${EnvironmentEndpoint.NAME}")

        then:
        HttpClientResponseException responseException = thrown()
        responseException.status.code == 401
    }

    void "it returns all the environment information"() {
        given:
        this.client = buildClient('test.filter': 'legacy')

        when:
        Map result = call("/${EnvironmentEndpoint.NAME}")
result.each { println "$it.key => $it.value"}
        then:
        result.activeEnvironments == ["test"]
        result.packages.contains("io.micronaut.management.endpoint.env")
        result.propertySources.size() == 3
        result.propertySources.find { it.name == 'context' }.properties['foo.bar'] == 'baz'
    }

    void "it returns all the properties of a property source"() {
        given:
        this.client = buildClient('test.filter': 'legacy')

        when:
        Map result = call()

        then:
        result.order == 0
        result.properties['foo.bar'] == 'baz'
    }

    void "it returns not found if the property source doesn't exist"() {
        given:
        this.client = buildClient('test.filter': 'legacy')

        when:
        call("/${EnvironmentEndpoint.NAME}/blah")

        then:
        HttpClientResponseException responseException = thrown()
        responseException.status.code == 404
    }

    void "it masks sensitive values"() {
        given:
        this.embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.env.enabled'  : true,
                'endpoints.env.sensitive': false,
                'foo.bar'                : 'baz',
                'my.password'            : '1234',
                'loginCredentials'       : 'blah',
                'CLIENT_CERTIFICATE'     : 'longString',
                'appKey'                 : 'app',
                'appSecret'              : 'app',
                'apiToken'               : 'token',
                'test.filter'            : 'legacy'
        ], Environment.TEST)
        this.client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        Map result = call()

        then:
        result.properties == [
                'endpoints.env.enabled'  : true,
                'endpoints.env.sensitive': false,
                'foo.bar'                : 'baz',
                'my.password'            : '*****',
                'loginCredentials'       : '*****',
                'CLIENT_CERTIFICATE'     : '*****',
                'appKey'                 : '*****',
                'appSecret'              : '*****',
                'apiToken'               : '*****',
                'test.filter'            : 'legacy'
        ]
    }

    @Singleton
    @Requires(property = "test.filter", value = "legacy")
    static class LegacyEnvironmentEndpointFilter implements EnvironmentEndpointFilter {

        @Override
        void specifyFiltering(@NonNull EnvironmentFilterSpecification specification) {
            specification.legacyMasking()
        }
    }

    void "if enabled but with no defined filter, all properties are masked"() {
        given:
        this.client = buildClient()

        when:
        Map result = client.exchange("/${EnvironmentEndpoint.NAME}", Map).blockFirst().body()

        then:
        result.activeEnvironments == ["test"]
        result.packages.contains("io.micronaut.management.endpoint.env")
        result.propertySources.size() == 3
        result.propertySources*.properties.collectEntries().values().every { it == '*****' }
    }

    void "individual properties can be masked"() {
        given:
        this.client = buildClient(
                'test.filter': 'individual',
                'foo.bar': 'baz',
                'my.password': '1234',
                iShouldBeMasked: 'some.value'
        )

        when:
        Map result = call()

        then:
        result.properties == [
                'endpoints.env.enabled': true,
                'endpoints.env.sensitive': false,
                'test.filter': 'individual',
                'foo.bar'        : 'baz',
                'my.password'    : '1234',
                'iShouldBeMasked': '*****'
        ]
    }

    @Singleton
    @Requires(property = "test.filter", value = "individual")
    static class IndividualEnvironmentEndpointFilter implements EnvironmentEndpointFilter {

        @Override
        void specifyFiltering(@NonNull EnvironmentFilterSpecification specification) {
            specification.maskNone().exclude("iShouldBeMasked")
        }
    }

    void "can chain extra masking off of the legacy filter"() {
        given:
        this.client = buildClient(
                'test.filter': 'legacy-plus-more',
                'foo.bar': 'baz',
                'my.password': '1234',
                iShouldBeMasked: 'some.value'
        )

        when:
        Map result = call()

        then:
        result.properties['foo.bar'] == 'baz'
        result.properties['my.password'] == '*****' // Caught by the legacy filter
        result.properties['iShouldBeMasked'] == '*****'
    }

    @Singleton
    @Requires(property = "test.filter", value = "legacy-plus-more")
    static class IndividualPlusLegacyEnvironmentEndpointFilter implements EnvironmentEndpointFilter {

        @Override
        void specifyFiltering(@NonNull EnvironmentFilterSpecification specification) {
            specification.legacyMasking().exclude('iShouldBeMasked')
        }
    }

    void "can mask all, with a set of allowed patterns"() {
        given:
        this.client = buildClient(
                'test.filter': 'mask-all-except',
                'foo.bar': 'baz',
                'my.password': '1234',
                dontMaskMe: 'some.value',
        )

        when:
        Map result = call()

        then:
        result.properties == [
                'endpoints.env.enabled'  : '*****',
                'endpoints.env.sensitive': '*****',
                'foo.bar'                : '*****',
                'test.filter'            : '*****',
                'my.password'            : '*****',
                'dontMaskMe'             : 'some.value'
        ]
    }

    void "shows only activeEnvironments when endpoints.env.active-keys=activeEnvironments"() {
        given:
        this.client = buildClient(['endpoints.env.active-keys': EnvironmentEndpoint.ACTIVE_ENVIRONMENTS_KEY])

        when:
        Map result = call("/${EnvironmentEndpoint.NAME}")

        then:
        result.containsKey(EnvironmentEndpoint.ACTIVE_ENVIRONMENTS_KEY)
        !result.containsKey(EnvironmentEndpoint.PACKAGES_KEY)
        !result.containsKey(EnvironmentEndpoint.PROPERTY_SOURCES_KEY)
        result.size() == 1
    }

    void "shows packages and propertySources when endpoints.env.active-keys=packages,propertySources"() {
        given:
        this.client = buildClient(['endpoints.env.active-keys': "${EnvironmentEndpoint.PACKAGES_KEY},${EnvironmentEndpoint.PROPERTY_SOURCES_KEY}"])

        when:
        Map result = call("/${EnvironmentEndpoint.NAME}")

        then:
        !result.containsKey(EnvironmentEndpoint.ACTIVE_ENVIRONMENTS_KEY)
        result.containsKey(EnvironmentEndpoint.PACKAGES_KEY)
        result.containsKey(EnvironmentEndpoint.PROPERTY_SOURCES_KEY)
        result.size() == 2
    }

    void "shows no sections when endpoints.env.active-keys is an empty string"() {
        given:
        this.client = buildClient(['endpoints.env.active-keys': '']) // Empty string

        when:
        Map result = call("/${EnvironmentEndpoint.NAME}")

        then:
        result.isEmpty()
    }

    void "uses default sections when endpoints.env.active-keys is not defined"() {
        given:
        // No 'endpoints.env.active-keys' property is set, so it should use the default.
        this.client = buildClient()

        when:
        Map result = call("/${EnvironmentEndpoint.NAME}")

        then:
        result.containsKey(EnvironmentEndpoint.ACTIVE_ENVIRONMENTS_KEY)
        result.containsKey(EnvironmentEndpoint.PACKAGES_KEY)
        result.containsKey(EnvironmentEndpoint.PROPERTY_SOURCES_KEY)
        result.size() == 3
    }

    void "ignores invalid section names and shows valid ones when endpoints.env.active-keys contains invalid section names"() {
        given:
        this.client = buildClient(['endpoints.env.active-keys': "invalidSection,${EnvironmentEndpoint.PACKAGES_KEY}"])

        when:
        Map result = call("/${EnvironmentEndpoint.NAME}")

        then:
        !result.containsKey(EnvironmentEndpoint.ACTIVE_ENVIRONMENTS_KEY)
        result.containsKey(EnvironmentEndpoint.PACKAGES_KEY)
        !result.containsKey(EnvironmentEndpoint.PROPERTY_SOURCES_KEY)
        !result.containsKey("invalidSection")
        result.size() == 1
    }

    @Singleton
    @Requires(property = "test.filter", value = "mask-all-except")
    static class HiddenAndMaskedEnvironmentEndpointFilter implements EnvironmentEndpointFilter {

        @Override
        void specifyFiltering(@NonNull EnvironmentFilterSpecification specification) {
            specification.maskAll().exclude('dontMaskMe')
        }
    }

    private Map call(String uri = "/${EnvironmentEndpoint.NAME}/context") {
        client.exchange(uri, Map).blockFirst().body()
    }

    private HttpClient buildClient(Map extraConfig = [:]) {
        this.embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.env.enabled': true, 'endpoints.env.sensitive': false, 'foo.bar': 'baz'] + extraConfig, Environment.TEST)
        embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
    }
}

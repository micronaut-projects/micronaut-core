package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.core.io.IOUtils
import spock.lang.Specification

class ConfigurationMetadataSpec extends Specification {

    void "test data class config metadata includes per-property descriptions"() {
        when:
        String json = metadataJson('test.VatConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import java.math.BigDecimal

/**
 * VAT configuration.
 *
 * @property percentage the percentage description text
 * @param region the region description text
 */
@ConfigurationProperties("vat")
data class VatConfig(val percentage: BigDecimal, val region: String)
''')

        then:
        json.contains('"name":"vat.percentage"')
        json.contains('"description":"the percentage description text"')
        json.contains('"description":"the region description text"')
    }

    void "test @ConfigurationInject class config metadata includes per-property descriptions"() {
        when:
        String json = metadataJson('test.ServerConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Server configuration.
 *
 * @param host the host description text
 * @param port the port description text
 */
@ConfigurationProperties("server")
class ServerConfig @ConfigurationInject constructor(val host: String, val port: Int)
''')

        then:
        json.contains('"name":"server.host"')
        json.contains('"description":"the host description text"')
        json.contains('"description":"the port description text"')
    }

    void "test data class group description is clean prose without leaked KDoc tags"() {
        when:
        String json = metadataJson('test.VatConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import java.math.BigDecimal

/**
 * VAT configuration.
 *
 * @property percentage the percentage description text
 * @param region the region description text
 */
@ConfigurationProperties("vat")
data class VatConfig(val percentage: BigDecimal, val region: String)
''')

        then:
        json.contains('"description":"VAT configuration."')
        !json.contains('@property')
        !json.contains('@param')
    }

    void "test data class config metadata carries the Bindable default value of a constructor parameter"() {
        when:
        String json = metadataJson('test.VatConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import java.math.BigDecimal

@ConfigurationProperties("vat")
data class VatConfig(@Bindable(defaultValue = "20") val percentage: BigDecimal)
''')

        then:
        json.contains('"name":"vat.percentage"')
        json.contains('"defaultValue":"20"')
    }

    void "test property-based config metadata description strips KDoc block tags"() {
        when:
        String json = metadataJson('test.ServerConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("server")
class ServerConfig {
    /**
     * The host to connect to.
     *
     * @since 2.0
     */
    var host: String? = null
}
''')

        then:
        json.contains('"name":"server.host"')
        json.contains('"description":"The host to connect to."')
        !json.contains('@since')
    }

    private static String metadataJson(String name, String source) {
        def classLoader = KotlinCompiler.buildClassLoader(name, source)
        def resource = classLoader.getResources("META-INF/spring-configuration-metadata.json").toList().last()
        return IOUtils.readText(new BufferedReader(new InputStreamReader(resource.openStream())))
    }
}

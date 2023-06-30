package io.micronaut.http.binary

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import spock.lang.Specification

class BinaryTypeConfigurationSpec extends Specification {

    ApplicationContext ctx

    def cleanup() {
        ctx?.stop()
    }

    def "test binary type configuration"() {
        when:
        def binaryTypeConfiguration = setCtx()

        then:
        binaryTypeConfiguration.isMediaTypeBinary(MediaType.IMAGE_GIF)
        !binaryTypeConfiguration.isMediaTypeBinary(MediaType.APPLICATION_XML)
    }

    def "defaults can be turned off"() {
        when:
        def binaryTypeConfiguration = setCtx(
                'micronaut.http.binary-types.use-defaults': 'false'
        )

        then:
        !binaryTypeConfiguration.isMediaTypeBinary(MediaType.IMAGE_GIF)
        !binaryTypeConfiguration.isMediaTypeBinary(MediaType.APPLICATION_XML)

        cleanup:
        ctx.stop()
    }

    def "extra types can be added"() {
        when:
        def binaryTypeConfiguration = setCtx(
                'micronaut.http.binary-types.additional-types': [MediaType.APPLICATION_XML, MediaType.TEXT_HTML]
        )

        then:
        binaryTypeConfiguration.isMediaTypeBinary(MediaType.IMAGE_GIF)
        binaryTypeConfiguration.isMediaTypeBinary(MediaType.APPLICATION_XML)
        binaryTypeConfiguration.isMediaTypeBinary(MediaType.TEXT_HTML)

        cleanup:
        ctx.stop()
    }

    def "extra types can be added AND defaults can be turned off"() {
        when:
        def binaryTypeConfiguration = setCtx(
                'micronaut.http.binary-types.use-defaults': 'false',
                'micronaut.http.binary-types.additional-types': [MediaType.APPLICATION_XML, MediaType.TEXT_HTML]
        )

        then:
        !binaryTypeConfiguration.isMediaTypeBinary(MediaType.IMAGE_GIF)
        binaryTypeConfiguration.isMediaTypeBinary(MediaType.APPLICATION_XML)
        binaryTypeConfiguration.isMediaTypeBinary(MediaType.TEXT_HTML)

        cleanup:
        ctx.stop()
    }

    private BinaryTypeConfiguration setCtx(Map<String, ?> properties = [:]) {
        ctx = ApplicationContext.run(properties)
        ctx.getBean(BinaryTypeConfiguration)
    }
}

package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import spock.lang.Specification

class SocketAddressSpec extends Specification {

    void "test socket address converter"() {
        def ctx = ApplicationContext.run()
        ConversionService converter = ctx.getBean(ConversionService)

        when:
        Optional<SocketAddress> address = converter.convert("1.2.3.4:8080", SocketAddress)

        then:
        address.isPresent()
        address.get() instanceof InetSocketAddress
        ((InetSocketAddress) address.get()).getHostName() == "1.2.3.4"
        ((InetSocketAddress) address.get()).getPort() == 8080

        when:
        address = converter.convert("https://foo.bar:8081", SocketAddress)

        then:
        address.isPresent()
        address.get() instanceof InetSocketAddress
        ((InetSocketAddress) address.get()).getHostName() == "foo.bar"
        ((InetSocketAddress) address.get()).getPort() == 8081

        when:
        ConversionContext conversionContext = ConversionContext.of(SocketAddress)
        address = converter.convert("abc:456456456456", conversionContext)

        then:
        !address.isPresent()
        conversionContext.lastError.get().cause instanceof IllegalArgumentException

        cleanup:
        ctx.close()
    }
}

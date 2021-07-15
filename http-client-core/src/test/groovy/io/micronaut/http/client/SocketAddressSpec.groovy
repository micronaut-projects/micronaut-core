package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import spock.lang.Specification

class SocketAddressSpec extends Specification {

    void "test socket address converter"() {
        def ctx = ApplicationContext.run()
        ConversionService converter = ctx.getBean(ConversionService)

        when:
        Optional<SocketAddress> address = converter.convert("1.2.3.4:8080", SocketAddress.class)

        then:
        address.isPresent()
        address.get() instanceof InetSocketAddress
        ((InetSocketAddress) address.get()).getHostName() == "1.2.3.4"
        ((InetSocketAddress) address.get()).getPort() == 8080

        when:
        address = converter.convert("abc", SocketAddress.class)

        then:
        !address.isPresent()

        when:
        converter.convert("abc:456456456456", SocketAddress.class)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        ctx.close()
    }
}

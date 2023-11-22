package io.micronaut.http.server

import io.micronaut.core.order.OrderUtil
import io.micronaut.core.order.Ordered
import io.micronaut.http.server.cors.CorsFilter
import io.micronaut.http.server.util.HttpHostResolver
import spock.lang.Specification

class OptionsFilterSpec extends Specification {

    void "OptionsFilter after CorsFilter"() {
        given:
        OptionsFilter optionsFilter = new OptionsFilter()
        CorsFilter corsFilter = new CorsFilter(Mock(HttpServerConfiguration.CorsConfiguration), Mock(HttpHostResolver))

        when:
        List<Ordered> filters = [optionsFilter, corsFilter]
        OrderUtil.sort(filters)

        then:
        filters[0] instanceof CorsFilter
        filters[1] instanceof OptionsFilter

        when:
        filters = [corsFilter, optionsFilter]
        OrderUtil.sort(filters)

        then:
        filters[0] instanceof CorsFilter
        filters[1] instanceof OptionsFilter
    }
}

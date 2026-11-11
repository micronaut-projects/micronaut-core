package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.server.filter.ResponseHeaderPopulator
import spock.lang.Specification

/**
 * Without a configured cross-origin policy there is no response-header populator, so the response
 * filter that applies populators is not registered either.
 */
class CrossOriginPolicyNotConfiguredSpec extends Specification {
    static final String FILTER = 'io.micronaut.http.server.filter.ResponseHeaderPopulatorFilter'

    def 'no populator and no populator filter without a cross-origin policy'() {
        given:
        def ctx = ApplicationContext.run()

        expect:
        ctx.getBeansOfType(ResponseHeaderPopulator).isEmpty()
        !ctx.containsBean(Class.forName(FILTER))

        cleanup:
        ctx.close()
    }

    def 'a configured cross-origin policy registers the populator and its filter'() {
        given:
        def ctx = ApplicationContext.run([(property): value])

        expect:
        ctx.getBeansOfType(ResponseHeaderPopulator).size() == 1
        ctx.containsBean(Class.forName(FILTER))

        cleanup:
        ctx.close()

        where:
        property                                            | value
        'micronaut.server.cors.cross-origin-resource-policy' | 'same-site'
        'micronaut.server.cors.cross-origin-embedder-policy' | 'require-corp'
    }
}

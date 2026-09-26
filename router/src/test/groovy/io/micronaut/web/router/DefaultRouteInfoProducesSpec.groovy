package io.micronaut.web.router

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.ReturnType
import io.micronaut.http.MediaType
import io.micronaut.http.body.MessageBodyHandlerRegistry
import spock.lang.Specification
import spock.lang.Unroll

class DefaultRouteInfoProducesSpec extends Specification {

    private static DefaultRouteInfo<String> route(List<MediaType> produces) {
        new DefaultRouteInfo<String>(AnnotationMetadata.EMPTY_METADATA, ReturnType.of(String), List.of(), produces,
                DefaultRouteInfoProducesSpec, false, false, MessageBodyHandlerRegistry.EMPTY)
    }

    @Unroll
    void "doesProduce #accept for route producing #produces is #expected, also when asked repeatedly"() {
        given:
        def info = route(produces.collect { MediaType.of(it) })
        def acceptable = MediaType.orderedOf(accept)

        expect:
        info.doesProduce(acceptable) == expected
        info.doesProduce(acceptable) == expected
        info.doesProduce(MediaType.orderedOf(accept)) == expected

        where:
        produces                          | accept                                  | expected
        ['application/json']              | 'application/json'                      | true
        ['application/json']              | 'text/html,application/json;q=0.9'      | true
        ['application/json']              | 'text/html,application/xml'             | false
        ['application/json']              | 'text/html,*/*;q=0.1'                   | true
        ['text/plain', 'application/xml'] | 'application/json,application/xml;q=.5' | true
        ['text/plain']                    | 'application/json,text/html'            | false
        ['text/plain']                    | ''                                      | true
    }

    void "cached result is keyed by list identity, not reused for a different accept list"() {
        given:
        def info = route([MediaType.APPLICATION_JSON_TYPE])
        def matching = MediaType.orderedOf('text/html,application/json')
        def notMatching = MediaType.orderedOf('text/html,application/xml')

        expect:
        info.doesProduce(matching)
        !info.doesProduce(notMatching)
        info.doesProduce(matching)
        !info.doesProduce(notMatching)
    }

    void "mutable accept lists are always re-evaluated"() {
        given:
        def info = route([MediaType.APPLICATION_JSON_TYPE])
        def accept = new ArrayList<MediaType>([MediaType.TEXT_HTML_TYPE, MediaType.APPLICATION_JSON_TYPE])

        expect:
        info.doesProduce(accept)

        when:
        accept.set(1, MediaType.APPLICATION_XML_TYPE)

        then:
        !info.doesProduce(accept)
    }

    void "multi-value accept headers are parsed into immutable lists"() {
        when:
        MediaType.orderedOf('text/html,application/json').add(MediaType.TEXT_PLAIN_TYPE)

        then:
        thrown(UnsupportedOperationException)
    }
}

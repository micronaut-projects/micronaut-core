package io.micronaut.core.io.service

import io.micronaut.core.beans.BeanIntrospectionReference
import spock.lang.Specification

class SoftServiceLoaderSpec extends Specification {
    def 'bean references show up in iterator'() {
        given:
        def loader = SoftServiceLoader.load(BeanIntrospectionReference)
        def iterator = loader.iterator()

        expect:
        iterator.hasNext()
    }
}

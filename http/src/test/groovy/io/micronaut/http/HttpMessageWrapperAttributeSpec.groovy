package io.micronaut.http

import io.micronaut.core.convert.ConversionService
import spock.lang.Specification

/**
 * The message wrappers read single attributes through the wrapped message, so that a message that
 * creates its attribute map lazily is not forced to create it.
 */
class HttpMessageWrapperAttributeSpec extends Specification {

    def "getAttribute delegates to the wrapped response"() {
        given:
        HttpResponse<?> delegate = Mock(HttpResponse)
        def wrapper = new HttpResponseWrapper(new HttpResponseWrapper(delegate))

        when:
        def untyped = wrapper.getAttribute('foo')
        def typed = wrapper.getAttribute('bar', Boolean)
        def missing = wrapper.getAttribute('baz', Boolean)

        then:
        1 * delegate.getAttribute('foo') >> Optional.of('value')
        1 * delegate.getAttribute('bar', Boolean) >> Optional.of(true)
        1 * delegate.getAttribute('baz', Boolean) >> Optional.empty()
        0 * delegate.getAttributes()
        untyped.get() == 'value'
        typed.get()
        missing.isEmpty()
    }

    def "getAttribute delegates to the wrapped request"() {
        given:
        HttpRequest<?> delegate = Mock(HttpRequest)
        def wrapper = MutableHttpRequestWrapper.wrapIfNecessary(ConversionService.SHARED, new HttpRequestWrapper(delegate))

        when:
        def untyped = wrapper.getAttribute('foo')
        def typed = wrapper.getAttribute('bar', String)

        then:
        1 * delegate.getAttribute('foo') >> Optional.empty()
        1 * delegate.getAttribute('bar', String) >> Optional.of('x')
        0 * delegate.getAttributes()
        untyped.isEmpty()
        typed.get() == 'x'
    }

    def "an empty name is not looked up"() {
        given:
        HttpResponse<?> delegate = Mock(HttpResponse)
        def wrapper = new HttpResponseWrapper(delegate)

        expect:
        wrapper.getAttribute('').isEmpty()
        wrapper.getAttribute('', String).isEmpty()
    }
}

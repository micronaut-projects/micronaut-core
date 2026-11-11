package io.micronaut.http.client.exceptions

import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.exceptions.NoAvailableServiceException
import spock.lang.Specification

class UnprocessedRequestExceptionSpec extends Specification {

    void "the first target set is kept"() {
        given:
        def e = new UnprocessedRequestException(UnprocessedRequestException.Reason.CONNECT, "Connect Error", null)
        def first = ServiceInstance.of("foo", URI.create("http://first:8080"))

        expect:
        !e.uri.present
        !e.serviceInstance.present

        when:
        e.setTarget(URI.create("http://first:8080/a"), first)
        e.setTarget(URI.create("http://second:8080/a"), ServiceInstance.of("foo", URI.create("http://second:8080")))

        then:
        e.reason == UnprocessedRequestException.Reason.CONNECT
        e.uri.get() == URI.create("http://first:8080/a")
        e.serviceInstance.get().is(first)
    }

    void "#error is unprocessed: #unprocessed"() {
        expect:
        UnprocessedRequestException.isUnprocessed(error) == unprocessed

        where:
        error                                                                                          | unprocessed
        new UnprocessedRequestException(UnprocessedRequestException.Reason.POOL_ACQUIRE, "Pool", null) | true
        new NoAvailableServiceException("foo")                                                         | true
        new HttpClientException("foo")                                                                 | false
        null                                                                                           | false
    }
}

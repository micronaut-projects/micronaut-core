package io.micronaut.http.client.exceptions

import io.micronaut.http.client.ServiceHttpClientConfiguration
import spock.lang.Specification

class HttpClientExceptionUtilsSpec extends Specification {

    void "verify serviceId gets populated"() {
        given:
        def config = Stub(ServiceHttpClientConfiguration) {
            getServiceId() >> 'bar'
        }
        expect:
        "bar" == HttpClientExceptionUtils.populateServiceId(new HttpClientException("foo"), "bar", null).serviceId
        "bar" == HttpClientExceptionUtils.populateServiceId(new HttpClientException("foo"), null, config).serviceId
        null == HttpClientExceptionUtils.populateServiceId(new HttpClientException("foo"), null, null).serviceId

        and: 'client id takes precedence'
        "fii" == HttpClientExceptionUtils.populateServiceId(new HttpClientException("foo"), "fii", config).serviceId
    }
}

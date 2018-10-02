package io.micronaut.http

import spock.lang.Specification

class HttpHeadersSpec extends Specification {

    def "HttpHeaders.accept() returns a list of media type for a comma separated string"() {
        when:
        HttpRequest request = HttpRequest.GET("/").header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        List<MediaType> mediaTypeList = request.headers.accept()

        then:
        mediaTypeList
        mediaTypeList.size() == 4

        mediaTypeList.find { it.name == 'text/html' && it.qualityAsNumber == 1.0 }
        mediaTypeList.find { it.name == 'application/xhtml+xml' && it.qualityAsNumber == 1.0 }
        mediaTypeList.find { it.name == 'application/xml' && it.qualityAsNumber == 0.9 }
        mediaTypeList.find { it.name == '*/*' && it.qualityAsNumber == 0.8 }
    }

    def "HttpHeaders.accept() returns a list of media type with one item for application/json"() {
        when:
        HttpRequest request = HttpRequest.GET("/").header("Accept", "application/json")
        List<MediaType> mediaTypeList = request.headers.accept()

        then:
        mediaTypeList
        mediaTypeList.size() == 1

        mediaTypeList.find { it.name == 'application/json' && it.qualityAsNumber == 1.0 }
    }

}
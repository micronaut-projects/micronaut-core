/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http

import spock.lang.Specification
import spock.lang.Unroll

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

    @Unroll
    void "HttpHeader.STANDARD_NAMES contains HTTP Header #httpHeaderName"(String httpHeaderName) {
        expect:
        HttpHeaders.STANDARD_HEADERS.contains(httpHeaderName)

        where:
        httpHeaderName << [
        HttpHeaders.ACCEPT,
        HttpHeaders.ACCEPT_CH,
        HttpHeaders.ACCEPT_CH_LIFETIME,
        HttpHeaders.ACCEPT_CHARSET,
        HttpHeaders.ACCEPT_ENCODING,
        HttpHeaders.ACCEPT_LANGUAGE,
        HttpHeaders.ACCEPT_RANGES,
        HttpHeaders.ACCEPT_PATCH,
        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
        HttpHeaders.ACCESS_CONTROL_MAX_AGE,
        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
        HttpHeaders.AGE,
        HttpHeaders.ALLOW,
        HttpHeaders.AUTHORIZATION,
        HttpHeaders.AUTHORIZATION_INFO,
        HttpHeaders.CACHE_CONTROL,
        HttpHeaders.CONNECTION,
        HttpHeaders.CONTENT_BASE,
        HttpHeaders.CONTENT_DISPOSITION,
        HttpHeaders.CONTENT_DPR,
        HttpHeaders.CONTENT_ENCODING,
        HttpHeaders.CONTENT_LANGUAGE,
        HttpHeaders.CONTENT_LENGTH,
        HttpHeaders.CONTENT_LOCATION,
        HttpHeaders.CONTENT_TRANSFER_ENCODING,
        HttpHeaders.CONTENT_MD5,
        HttpHeaders.CONTENT_RANGE,
        HttpHeaders.CONTENT_TYPE,
        HttpHeaders.COOKIE,
        HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY,
        HttpHeaders.DATE,
        HttpHeaders.DEVICE_MEMORY,
        HttpHeaders.DOWNLINK,
        HttpHeaders.DPR,
        HttpHeaders.ECT,
        HttpHeaders.ETAG,
        HttpHeaders.EXPECT,
        HttpHeaders.EXPIRES,
        HttpHeaders.FEATURE_POLICY,
        HttpHeaders.FORWARDED,
        HttpHeaders.FROM,
        HttpHeaders.HOST,
        HttpHeaders.IF_MATCH,
        HttpHeaders.IF_MODIFIED_SINCE,
        HttpHeaders.IF_NONE_MATCH,
        HttpHeaders.IF_RANGE,
        HttpHeaders.IF_UNMODIFIED_SINCE,
        HttpHeaders.LAST_MODIFIED,
        HttpHeaders.LINK,
        HttpHeaders.LOCATION,
        HttpHeaders.MAX_FORWARDS,
        HttpHeaders.ORIGIN,
        HttpHeaders.PRAGMA,
        HttpHeaders.PROXY_AUTHENTICATE,
        HttpHeaders.PROXY_AUTHORIZATION,
        HttpHeaders.RANGE,
        HttpHeaders.REFERER,
        HttpHeaders.REFERRER_POLICY,
        HttpHeaders.RETRY_AFTER,
        HttpHeaders.RTT,
        HttpHeaders.SAVE_DATA,
        HttpHeaders.SEC_WEBSOCKET_KEY1,
        HttpHeaders.SEC_WEBSOCKET_KEY2,
        HttpHeaders.SEC_WEBSOCKET_LOCATION,
        HttpHeaders.SEC_WEBSOCKET_ORIGIN,
        HttpHeaders.SEC_WEBSOCKET_PROTOCOL,
        HttpHeaders.SEC_WEBSOCKET_VERSION,
        HttpHeaders.SEC_WEBSOCKET_KEY,
        HttpHeaders.SEC_WEBSOCKET_ACCEPT,
        HttpHeaders.SERVER,
        HttpHeaders.SET_COOKIE,
        HttpHeaders.SET_COOKIE2,
        HttpHeaders.SOURCE_MAP,
        HttpHeaders.TE,
        HttpHeaders.TRAILER,
        HttpHeaders.TRANSFER_ENCODING,
        HttpHeaders.UPGRADE,
        HttpHeaders.USER_AGENT,
        HttpHeaders.VARY,
        HttpHeaders.VIA,
        HttpHeaders.VIEWPORT_WIDTH,
        HttpHeaders.WARNING,
        HttpHeaders.WEBSOCKET_LOCATION,
        HttpHeaders.WEBSOCKET_ORIGIN,
        HttpHeaders.WEBSOCKET_PROTOCOL,
        HttpHeaders.WIDTH,
        HttpHeaders.WWW_AUTHENTICATE,
        HttpHeaders.X_AUTH_TOKEN,
                ]
    }
}

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
package io.micronaut.validation

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriTemplate
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BookmarkControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())


    void "test parameters binding"() {
        when:
        UriTemplate template = new UriTemplate("/api/bookmarks{?offset,max,sort,order}")
        String uri = template.expand([offset: -1])

        then:
        uri == '/api/bookmarks?offset=-1'

        when:
        HttpRequest request = HttpRequest.GET(uri)
        client.toBlocking().exchange(request)

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST
    }

    void "test parameters binding allows nullable"() {
        when:
        UriTemplate template = new UriTemplate("/api/bookmarks{?offset,max,sort,order}")
        String uri = template.expand([:])

        then:
        uri == '/api/bookmarks'

        when:
        HttpRequest request = HttpRequest.GET(uri)
        HttpResponse rsp = client.toBlocking().exchange(request)

        then:
        rsp.status == HttpStatus.OK
    }

    void "test POJO binding allows nullable"() {
        when:
        UriTemplate template = new UriTemplate("/api/bookmarks/list{?offset,max,sort,order}")
        String uri = template.expand([:])

        then:
        uri == '/api/bookmarks/list'

        when:
        HttpRequest request = HttpRequest.GET(uri)
        HttpResponse rsp = client.toBlocking().exchange(request)

        then:
        rsp.status == HttpStatus.OK
    }

    void "test POJO binding"() {
        when:
        UriTemplate template = new UriTemplate("/api/bookmarks/list{?offset,max,sort,order}")
        String uri = template.expand([offset: -1, max: 10])

        then:
        uri == '/api/bookmarks/list?offset=-1&max=10'

        when:
        HttpRequest request = HttpRequest.GET(uri)
        client.toBlocking().exchange(request)

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST
    }

    void "test POJO binding with valid values"() {
        when:
        UriTemplate template = new UriTemplate("/api/bookmarks/list{?sort,order,max,offset,ids*}")
        String uri = template.expand([sort: 'href', order: 'desc', max: 2, offset: 0, ids: [1]]) // , 2

        then:
        uri == '/api/bookmarks/list?sort=href&order=desc&max=2&offset=0&ids=1'

        when:
        HttpRequest request = HttpRequest.GET(uri)
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

        when:
        Map m = rsp.body()

        then:
        m.offset == 0
        m.max == 2
        m.order == 'desc'
        m.sort == 'href'
        m.ids == [1]
    }
}

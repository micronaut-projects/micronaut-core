/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Controller
import org.particleframework.runtime.server.EmbeddedServer
import org.particleframework.http.annotation.Delete
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * TODO: Javadoc description
 *
 * @author graemerocher
 * @since 1.0
 */
class HttpDeleteSpec extends Specification {


    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())


    void "test http delete"() {
        when:
        def res = Flowable.fromPublisher(client.exchange(HttpRequest.DELETE('/delete/simple'))).blockingFirst()

        then:
        res.status == HttpStatus.NO_CONTENT
    }


    void "test http delete with body"() {
        when:
        def res = Flowable.fromPublisher(client.exchange(
                HttpRequest.DELETE('/delete/body', 'test')
                           .contentType(MediaType.TEXT_PLAIN) , String)).blockingFirst()
        def body = res.body
        then:
        res.status == HttpStatus.ACCEPTED
        body.isPresent()
        body.get() == 'test'
    }

    @Controller("/delete")
    static class DeleteController {

        @Delete(uri = "/simple")
        HttpResponse simple() {
            HttpResponse.noContent()
        }

        @Delete(uri = "/body", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        HttpResponse simple(@Body String content) {

            HttpResponse.accepted()
                        .body(content)
        }
    }
}

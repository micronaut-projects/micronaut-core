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
import org.particleframework.http.HttpRequest
import spock.lang.Ignore
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HttpPostSpec extends Specification {

    @Ignore
    void "test simple post request with JSON"() {
        given:
        HttpClient client = null

        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.post("/post/simple", new Book(title: "The Stand"))
                           .header("X-My-Header", "Foo")
        ))
        Optional<String> body = flowable.blockingFirst().getBody(String.class)

        then:
        body.isPresent()
        body.get() == 'success'

    }

    static class Book {
        String title
    }
}

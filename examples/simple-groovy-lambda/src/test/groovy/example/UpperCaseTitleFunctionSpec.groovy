/*
 * Copyright 2017 original authors
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
package example

import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpRequest
import org.particleframework.http.client.HttpClient
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class UpperCaseTitleFunctionSpec extends Specification {

    void "run function directly"() {
        expect:
        new UpperCaseTitleFunction()
                .toUpperCase(new Book(title: "The Stand")) == new Book(title: "THE STAND")
    }

    void "run function as REST service"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpClient client = server.getApplicationContext().createBean(HttpClient, server.getURL())

        when:
        Book book = client.toBlocking()
                          .retrieve(HttpRequest.POST("/upper-case-title", new Book(title: "The Stand")), Book)

        then:
        book.title == "THE STAND"

        cleanup:
        if(server != null)
            server.stop()
    }
}

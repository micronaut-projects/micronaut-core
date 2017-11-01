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
package org.particleframework.function.aws

import com.amazonaws.services.lambda.runtime.Context
import org.particleframework.context.ApplicationContext
import org.particleframework.context.env.Environment
import org.particleframework.function.Function
import spock.lang.Specification


/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ParticleRequestStreamHandlerSpec extends Specification{

    void "test particle stream handler"() {
        given:
        ParticleRequestStreamHandler requestStreamHandler = new ParticleRequestStreamHandler() {
            @Override
            protected String resolveFunctionName(Environment environment) {
                return "book"
            }
        }


        when:
        def body = '{"title":"The Stand"}'
        def output = new ByteArrayOutputStream()
        requestStreamHandler.handleRequest(
                new ByteArrayInputStream(body.bytes),
                output,
                Mock(Context)
        )

        then:
        output.toString() == '{"title":"THE STAND"}'
    }


    static class Book {
        String title
    }

    @Function('book')
    static class BookFunction implements java.util.function.Function<Book, Book> {

        @Override
        Book apply(Book book) {
            book.title = book.title.toUpperCase()
            return book
        }
    }
}

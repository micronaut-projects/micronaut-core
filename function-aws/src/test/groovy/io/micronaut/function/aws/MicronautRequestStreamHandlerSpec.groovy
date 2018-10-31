/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.function.aws

import com.amazonaws.services.lambda.runtime.Context
import io.micronaut.context.env.Environment
import io.micronaut.function.FunctionBean
import spock.lang.Specification

import java.util.function.Function


/**
 * @author Graeme Rocher
 * @since 1.0
 */
class MicronautRequestStreamHandlerSpec extends Specification{

    void "test micronaut stream handler with POJO"() {
        given:
        MicronautRequestStreamHandler requestStreamHandler = new MicronautRequestStreamHandler() {
            @Override
            protected String resolveFunctionName(Environment environment) {
                return "book"
            }
        }


        when:
        def body = '{"title":"The Stand"}'
        def input = new ByteArrayInputStream()
        input.text
        def output = new ByteArrayOutputStream()
        requestStreamHandler.handleRequest(
                new ByteArrayInputStream(body.bytes),
                output,
                Mock(Context)
        )

        then:
        output.toString() == '{"title":"THE STAND"}'

        when:"invoked for a second time"
        body = '{"title":"The Stand"}'
        input = new ByteArrayInputStream()
        input.text
        output = new ByteArrayOutputStream()
        requestStreamHandler.handleRequest(
                new ByteArrayInputStream(body.bytes),
                output,
                Mock(Context)
        )

        then:
        output.toString() == '{"title":"THE STAND"}'
    }

    void "test micronaut stream handler with integer"() {
        given:
        MicronautRequestStreamHandler requestStreamHandler = new MicronautRequestStreamHandler() {
            @Override
            protected String resolveFunctionName(Environment environment) {
                return "multiply-by-two"
            }
        }


        when:
        def body = '10'
        def input = new ByteArrayInputStream()
        input.text
        def output = new ByteArrayOutputStream()
        requestStreamHandler.handleRequest(
                new ByteArrayInputStream(body.bytes),
                output,
                Mock(Context)
        )

        then:
        output.toString() == 'value 20'
    }

    static class Book {
        String title
    }

    @FunctionBean('book')
    static class BookFunction implements Function<Book, Book> {

        @Override
        Book apply(Book book) {
            book.title = book.title.toUpperCase()
            return book
        }
    }

    @FunctionBean('multiply-by-two')
    static class IntegerFunction implements Function<Integer, String> {

        @Override
        String apply(Integer i) {
            return "value " + (i * 2);
        }
    }
}

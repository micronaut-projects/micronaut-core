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

import grails.gorm.transactions.Transactional
import io.micronaut.context.annotation.Value
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller
@Singleton
class BookController {

    @Inject
    BookService bookService

    @Value('${galecino.servo.trim:0.0}')
    Float trim

    @Get('/')
    List<Book> index() {
        bookService.list()
    }

    @Get(uri = '/trim', produces = MediaType.TEXT_PLAIN)
    String trim() {
        trim.toString()
    }



    @Transactional
    @PostConstruct
    void setup() {
        bookService.save 'The Stand'
    }
}

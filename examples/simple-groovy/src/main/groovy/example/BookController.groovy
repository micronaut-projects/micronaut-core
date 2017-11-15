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
import org.particleframework.http.annotation.Controller

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

    List<Book> index() {
        bookService.list()
    }

    @Transactional
    @PostConstruct
    void setup() {
        bookService.save 'The Stand'
    }
}

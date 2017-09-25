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

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.particleframework.stereotype.Controller

import javax.annotation.PostConstruct
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller
@Singleton
class BookController {

    @ReadOnly
    List<Book> index() {
        Book.list()
    }

    @Transactional
    @PostConstruct
    void setup() {
        new Book(title: "The Stand").save()
    }
}

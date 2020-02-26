/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.replaces

import io.micronaut.context.annotation.Replaces
import io.micronaut.docs.requires.Book

import javax.inject.Singleton
import java.util.LinkedHashMap

// tag::class[]
@Replaces(JdbcBookService::class) // <1>
@Singleton
class MockBookService : BookService {

    var bookMap: Map<String, Book> = LinkedHashMap()

    override fun findBook(title: String): Book? {
        return bookMap[title]
    }
}
// tag::class[]

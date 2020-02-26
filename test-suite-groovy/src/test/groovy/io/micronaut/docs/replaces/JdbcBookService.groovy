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

import groovy.sql.Sql
import io.micronaut.context.annotation.Requires
import io.micronaut.docs.requires.Book

import javax.inject.Singleton
import javax.sql.DataSource
import java.sql.SQLException

// tag::replaces[]
@Singleton
@Requires(beans = DataSource.class)
class JdbcBookService implements BookService {

    DataSource dataSource

// end::replaces[]

    @Override
    Book findBook(String title) {
        try {
            def sql = new Sql(dataSource)
            def result = sql.firstRow("select * from books where title =  $title")
            if(result) {
                return new Book(result.get('title'))
            }
        }
        catch (SQLException ex) {
            return null
        }
        return null
    }
}
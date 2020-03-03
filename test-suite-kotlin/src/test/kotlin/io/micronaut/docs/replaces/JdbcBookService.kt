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

import io.micronaut.context.annotation.Requires
import io.micronaut.docs.requires.Book

import javax.inject.Singleton
import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

// tag::replaces[]
@Singleton
@Requires(beans = [DataSource::class])
class JdbcBookService(internal var dataSource: DataSource) : BookService {

    // end::replaces[]

    override fun findBook(title: String): Book? {
        try {
            dataSource.connection.use { connection ->
                val ps = connection.prepareStatement("select * from books where title = ?")
                ps.setString(1, title)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    return Book(rs.getString("title"))
                }
            }
        } catch (ex: SQLException) {
            return null
        }

        return null
    }
}

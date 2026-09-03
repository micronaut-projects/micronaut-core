/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.requires

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

import java.sql.SQLException
import javax.sql.DataSource

// tag::requires[]
@Singleton
@Requires(beans = Array(classOf[DataSource]))
@Requires(property = "datasource.url")
class JdbcBookService(private val dataSource: DataSource) extends BookService:

// end::requires[]

  override def findBook(title: String): Book =
    try
      val connection = dataSource.getConnection()
      try
        val ps = connection.prepareStatement("select * from books where title = ?")
        ps.setString(1, title)
        val rs = ps.executeQuery()
        if rs.next() then Book(rs.getString("title")) else null
      finally connection.close()
    catch
      case _: SQLException => null

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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*

class RequiresSpec:

  @Test
  def jdbcBookServiceLoadsWhenRequirementsMatch(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object](
        "spec.name" -> "RequiresSpec",
        "datasource.url" -> "jdbc:test"
      ).asJava
    )
    try
      assertTrue(context.containsBean(classOf[JdbcBookService]))
      assertTrue(context.containsBean(classOf[BookService]))
    finally context.close()

  @Test
  def jdbcBookServiceDoesNotLoadWithoutDatasourceUrl(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "RequiresSpec").asJava
    )
    try
      assertFalse(context.containsBean(classOf[JdbcBookService]))
      assertFalse(context.containsBean(classOf[BookService]))
    finally context.close()

@Singleton
@Requires(property = "spec.name", value = "RequiresSpec")
class TestDataSource extends DataSource:
  override def getConnection(): Connection =
    throw SQLException("No test database is available")

  override def getConnection(username: String, password: String): Connection =
    getConnection()

  override def getLogWriter(): PrintWriter = null

  override def setLogWriter(out: PrintWriter): Unit = ()

  override def setLoginTimeout(seconds: Int): Unit = ()

  override def getLoginTimeout(): Int = 0

  override def getParentLogger(): Logger = Logger.getGlobal

  override def unwrap[T](iface: Class[T]): T =
    throw SQLException(s"${iface.getName} is not wrapped")

  override def isWrapperFor(iface: Class[?]): Boolean = false

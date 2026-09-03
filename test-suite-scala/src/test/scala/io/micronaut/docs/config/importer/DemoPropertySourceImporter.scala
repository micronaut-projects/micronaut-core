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
package io.micronaut.docs.config.importer

import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourceImporter
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.util.ConnectionString

import java.util.Optional
import scala.jdk.CollectionConverters.*

// tag::class[]
final class DemoPropertySourceImporter
    extends PropertySourceImporter[DemoPropertySourceImporter.DemoImport]:

  override def getProvider: String =
    "demo"

  override def newImportDeclaration(connectionString: ConnectionString): DemoPropertySourceImporter.DemoImport =
    DemoPropertySourceImporter.DemoImport(connectionString.getPath)

  override def newImportDeclaration(values: ConvertibleValues[Object]): DemoPropertySourceImporter.DemoImport =
    DemoPropertySourceImporter.DemoImport(values.get("path", classOf[String]).orElse("defaults"))

  override def importPropertySource(
      context: PropertySourceImporter.ImportContext[DemoPropertySourceImporter.DemoImport]
  ): Optional[PropertySource] =
    if context.importDeclaration().path != "defaults" then
      Optional.empty()
    else
      Optional.of(
        PropertySource.of(
          "demo:defaults",
          Map[String, Object]("demo.message" -> "hello-from-demo-importer").asJava
        )
      )

object DemoPropertySourceImporter:
  final case class DemoImport(path: String)
// end::class[]

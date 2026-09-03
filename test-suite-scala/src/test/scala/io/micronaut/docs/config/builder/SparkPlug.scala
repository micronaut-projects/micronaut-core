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
package io.micronaut.docs.config.builder

import java.util.Optional

final class SparkPlug(
    val name: Optional[String],
    val sparkType: Optional[String],
    val companyName: Optional[String]
):
  override def toString: String =
    s"${sparkType.orElse("")}(${companyName.orElse("")} ${name.orElse("")})"

object SparkPlug:
  def builder(): Builder = new Builder()

  final class Builder:
    private var name: Optional[String] = Optional.of("4504 PK20TT")
    private var sparkType: Optional[String] = Optional.of("Platinum TT")
    private var companyName: Optional[String] = Optional.of("Denso")

    def withName(name: String): Builder =
      this.name = Optional.ofNullable(name)
      this

    def withType(sparkType: String): Builder =
      this.sparkType = Optional.ofNullable(sparkType)
      this

    def withCompanyName(companyName: String): Builder =
      this.companyName = Optional.ofNullable(companyName)
      this

    def build(): SparkPlug = new SparkPlug(name, sparkType, companyName)

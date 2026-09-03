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
package io.micronaut.docs.language.scalasupport

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

// tag::dto[]
@Introspected
case class BookDto(title: String, subtitle: String | Null)
// end::dto[]

// tag::configuration[]
@ConfigurationProperties("reader")
case class ReaderConfig(
    name: String,
    favoriteGenres: List[String],
    labels: Map[String, String]
)
// end::configuration[]

// tag::validatedConfiguration[]
@ConfigurationProperties("validated.reader")
case class ValidatedReaderConfig(
    @NotBlank name: String
)
// end::validatedConfiguration[]

trait Engine

// tag::injection[]
@Requires(property = "spec.name", value = "ScalaLanguageSupportSpec")
@Singleton
class Garage(
    val engines: List[Engine],
    val enginesByName: Map[String, Engine],
    val selectedEngine: Option[Engine]
)
// end::injection[]

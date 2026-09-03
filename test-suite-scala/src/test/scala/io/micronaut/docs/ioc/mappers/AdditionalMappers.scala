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
package io.micronaut.docs.ioc.mappers

import io.micronaut.context.annotation.Mapper
import io.micronaut.context.annotation.Mapper.Mapping
import io.micronaut.context.annotation.Mapper.MergeStrategy
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess
import io.micronaut.docs.ioc.mappers.ChristmasTypes.ChristmasPresent
import io.micronaut.docs.ioc.mappers.ChristmasTypes.Present
import io.micronaut.docs.ioc.mappers.ChristmasTypes.PresentPackaging
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.util.{Map as JMap}

@Requires(property = "spec.name", value = "MappersSpec")
// tag::mapper[]
trait AdditionalMappers:

  @Mapper // <1>
  def merge(packaging: PresentPackaging, present: Present, christmasCard: Card): ChristmasPresent

  @Mapping(
    from = "#{updateFields['christmasCard'] + '!!'}", to = "greetingCard"
  ) // <2>
  def update(present: ChristmasPresent, updateFields: JMap[String, Object]): ChristmasPresent

  @Mapper(
    mergeStrategy = "add-numbers",
    value = Array(new Mapping(from = "packaging.color", to = "packagingColor"))
  ) // <3>
  def mergeWithMergeStrategy(packaging: PresentPackaging, present: Present): ChristmasPresent

@Singleton
@Named("add-numbers")
class MyMergeStrategy extends MergeStrategy:
  override def merge(
      currentValue: AnyRef,
      value: AnyRef,
      valueOwner: AnyRef,
      propertyName: String,
      mappedPropertyName: String
  ): AnyRef =
    (currentValue, value) match
      case (a: java.lang.Float, b: java.lang.Float) =>
        java.lang.Float.valueOf(a.floatValue() + b.floatValue())
      case _ => value

@ReflectiveAccess
@Introspected
case class Card(greetingCard: String)

// end::mapper[]

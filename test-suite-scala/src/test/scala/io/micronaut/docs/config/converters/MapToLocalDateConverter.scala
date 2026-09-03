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
package io.micronaut.docs.config.converters

// tag::imports[]
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.TypeConverter
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Optional
// end::imports[]

// tag::class[]
@Prototype
class MapToLocalDateConverter(conversionService: ConversionService)
    extends TypeConverter[java.util.Map[?, ?], LocalDate]: // <1>

  override def convert(
      propertyMap: java.util.Map[?, ?],
      targetType: Class[LocalDate],
      context: ConversionContext
  ): Optional[LocalDate] =
    val day = conversionService.convert(propertyMap.get("day"), classOf[Integer])
    val month = conversionService.convert(propertyMap.get("month"), classOf[Integer])
    val year = conversionService.convert(propertyMap.get("year"), classOf[Integer])

    if day.isPresent && month.isPresent && year.isPresent then
      try
        Optional.of(LocalDate.of(year.get(), month.get(), day.get())) // <3>
      catch
        case e: DateTimeException =>
          context.reject(propertyMap, e) // <4>
          Optional.empty()
    else
      Optional.empty()
// end::class[]

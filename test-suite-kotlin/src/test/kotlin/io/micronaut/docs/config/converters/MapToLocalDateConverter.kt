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
package io.micronaut.docs.config.converters

// tag::imports[]
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.TypeConverter

import javax.inject.Singleton
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Optional
// end::imports[]

// tag::class[]
@Singleton
class MapToLocalDateConverter : TypeConverter<Map<*, *>, LocalDate> { // <1>
    override fun convert(propertyMap: Map<*, *>, targetType: Class<LocalDate>, context: ConversionContext): Optional<LocalDate> {
        val day = ConversionService.SHARED.convert(propertyMap["day"], Int::class.java)
        val month = ConversionService.SHARED.convert(propertyMap["month"], Int::class.java)
        val year = ConversionService.SHARED.convert(propertyMap["year"], Int::class.java)
        if (day.isPresent && month.isPresent && year.isPresent) {
            try {
                return Optional.of(LocalDate.of(year.get(), month.get(), day.get())) // <2>
            } catch (e: DateTimeException) {
                context.reject(propertyMap, e) // <3>
                return Optional.empty()
            }

        }

        return Optional.empty()
    }
}
// end::class[]
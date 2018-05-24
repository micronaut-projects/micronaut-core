/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.core.convert.*
import java.time.*
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class MapToLocalDateConverter implements TypeConverter<Map, LocalDate> { // <1>
    @Override
    Optional<LocalDate> convert(Map object, Class<LocalDate> targetType, ConversionContext context) {
        Optional<Integer> day = ConversionService.SHARED.convert(object.get("day"), Integer.class)
        Optional<Integer> month = ConversionService.SHARED.convert(object.get("month"), Integer.class)
        Optional<Integer> year = ConversionService.SHARED.convert(object.get("year"), Integer.class)
        if (day.isPresent() && month.isPresent() && year.isPresent()) {
            try {
                return Optional.of(LocalDate.of(year.get(), month.get(), day.get())) // <2>
            } catch (DateTimeException e) {
                context.reject(object, e) // <3>
                return Optional.empty()
            }
        }
        return Optional.empty()
    }
}
// end::class[]
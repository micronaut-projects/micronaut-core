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
        if ( day.isPresent() && month.isPresent() && year.isPresent() ) {
            try {
                return Optional.of(LocalDate.of(year.get(), month.get(), day.get())) // <2>
            } catch ( DateTimeException e ) {
                context.reject(object, e) // <3>
                return Optional.empty()
            }
        }
        return Optional.empty()
    }
}
// end::class[]
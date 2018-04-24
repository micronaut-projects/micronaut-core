package io.micronaut.docs.config.converters

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import java.time.DateTimeException
import java.time.LocalDate
import javax.inject.Singleton

@Singleton
class LocalDateConverter implements TypeConverter<Map, LocalDate> {
    private static final String KEY_DAY = "day"
    private static final String KEY_MONTH = "month"
    private static final String KEY_YEAR = "year"

    @Override
    Optional<LocalDate> convert(Map object, Class<LocalDate> targetType, ConversionContext context) {
        Optional<Integer> day = findIntegerByKey(object, KEY_DAY)
        Optional<Integer> month = findIntegerByKey(object, KEY_MONTH)
        Optional<Integer> year = findIntegerByKey(object, KEY_YEAR)
        if ( day.isPresent() && month.isPresent() && year.isPresent() ) {
            try {
                return Optional.of(LocalDate.of(year.get(), month.get(), day.get()))
            } catch ( DateTimeException e ) {
                return Optional.empty()
            }
        }
        return Optional.empty()
    }

    private Optional<Integer> findIntegerByKey(Map m, String key) {
        if ( !m.containsKey(key) ) {
            return Optional.empty()
        }
        Object obj = m.get(key)
        try {
            if ( obj instanceof Integer) {
                return Optional.of((Integer)obj)
            } else if ( obj instanceof String) {
                return Optional.of(Integer.valueOf((String) obj))
            }
        } catch (NumberFormatException e) {
            return Optional.empty()
        }
        return Optional.empty()
    }
}

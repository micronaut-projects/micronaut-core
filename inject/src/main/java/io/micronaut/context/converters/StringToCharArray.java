package io.micronaut.context.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import java.util.Optional;

public class StringToCharArray implements TypeConverter<CharSequence, char[]> {
    @Override
    public Optional<char[]> convert(CharSequence object, Class<char[]> targetType, ConversionContext context) {
        return Optional.of(object.toString().toCharArray());
    }
}

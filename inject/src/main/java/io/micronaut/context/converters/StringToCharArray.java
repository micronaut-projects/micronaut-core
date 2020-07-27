package io.micronaut.context.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton // #2614
public class StringToCharArray implements TypeConverter<String,char[]> {

    @Override
    public Optional<char[]> convert(String object, Class<char[]> targetType, ConversionContext context) {
        return Optional.of(object.toCharArray());
    }
}
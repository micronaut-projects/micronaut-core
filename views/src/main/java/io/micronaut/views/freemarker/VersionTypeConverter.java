package io.micronaut.views.freemarker;

import freemarker.template.Version;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class VersionTypeConverter implements TypeConverter<CharSequence, Version> {

    @Override
    public Optional<Version> convert(CharSequence object, Class<Version> targetType, ConversionContext context) {
        return Optional.of(new Version(object.toString()));
    }
}

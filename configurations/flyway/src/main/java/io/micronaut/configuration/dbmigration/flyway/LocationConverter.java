package io.micronaut.configuration.dbmigration.flyway;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.Location;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class LocationConverter implements TypeConverter<CharSequence, Location> {

    @Override
    public Optional<Location> convert(CharSequence object, Class<Location> targetType, ConversionContext context) {
        try {
            return Optional.of(new Location(object.toString()));
        } catch (FlywayException e) {
            return Optional.empty();
        }
    }
}

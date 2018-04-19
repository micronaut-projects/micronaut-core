package io.micronaut.security.token.converters;

import com.nimbusds.jose.JWEAlgorithm;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class JWEAlgorithmConverter implements TypeConverter<CharSequence, JWEAlgorithm> {

    @Override
    public Optional<JWEAlgorithm> convert(CharSequence object, Class<JWEAlgorithm> targetType, ConversionContext context) {
        String value = object.toString();
        JWEAlgorithm algorithm = JWEAlgorithm.parse(value);
        //The algorithm was created by the parse method
        if (algorithm.getRequirement() != null) {
            return Optional.of(algorithm);
        } else {
            return Optional.empty();
        }
    }
}

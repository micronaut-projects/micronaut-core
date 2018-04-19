package io.micronaut.security.token.converters;

import com.nimbusds.jose.EncryptionMethod;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import static com.nimbusds.jose.EncryptionMethod.*;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class EncryptionMethodConverter implements TypeConverter<CharSequence, EncryptionMethod> {

    @Override
    public Optional<EncryptionMethod> convert(CharSequence object, Class<EncryptionMethod> targetType, ConversionContext context) {
        String value = object.toString();
        EncryptionMethod encryptionMethod = EncryptionMethod.parse(value);
        //The encryption method was just created by the parse method
        if (encryptionMethod.cekBitLength() == 0) {
            return Optional.empty();
        } else {
            return Optional.of(encryptionMethod);
        }
    }
}

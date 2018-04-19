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

package io.micronaut.security.token.converters;

import com.nimbusds.jose.EncryptionMethod;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import javax.inject.Singleton;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class EncryptionMethodConverter implements TypeConverter<CharSequence, EncryptionMethod> {

    @Override
    public Optional<EncryptionMethod> convert(CharSequence object, Class<EncryptionMethod> targetType, ConversionContext context) {
        if ( object == null ) {
            return Optional.empty();
        }
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

/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.xml.server.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/**
 * Converts {@link ByteArrayXmlStreamReader} to object.
 *
 * @author sergey.vishnyakov
 * @since 1.3.0
 */
@Singleton
@Internal
public class XmlStreamToObjectConverter implements TypeConverter<ByteArrayXmlStreamReader, Object> {

    private final XmlMapper xmlMapper;
    private final ConversionService<?> conversionService;

    @Inject
    public XmlStreamToObjectConverter(XmlMapper xmlMapper, ConversionService<?> conversionService) {
        this.xmlMapper = xmlMapper;
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Object> convert(ByteArrayXmlStreamReader stream, Class<Object> targetType, io.micronaut.core.convert.ConversionContext context) {
        try {
            if (ConvertibleValues.class.isAssignableFrom(targetType)) {
                return Optional.of(new XmlStreamConvertibleValues<>(stream, xmlMapper, conversionService));
            } else {
                return Optional.of(xmlMapper.readValue(stream, targetType));
            }
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}

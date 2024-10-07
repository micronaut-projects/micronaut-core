/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.convert.format;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * Converts String's to readable bytes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ReadableBytesTypeConverter implements FormattingTypeConverter<CharSequence, Number, ReadableBytes> {

    private static final String KILOBYTES = "KB";
    private static final String MEGABYTES = "MB";
    private static final String GIGABYTES = "GB";
    private static final int KB_UNIT = 1024;

    @Override
    public Class<ReadableBytes> annotationType() {
        return ReadableBytes.class;
    }

    @Override
    public Optional<Number> convert(CharSequence object, Class<Number> targetType, ConversionContext context) {
        if (StringUtils.isEmpty(object)) {
            return Optional.empty();
        }
        String value = object.toString().toUpperCase(Locale.ENGLISH);
        try {
            long size;
            if (value.endsWith(KILOBYTES)) {
                long numberPart = parseSizeWithUnit(value);
                size = numberPart * KB_UNIT;
            } else if (value.endsWith(MEGABYTES)) {
                long numberPart = parseSizeWithUnit(value);
                size = numberPart * KB_UNIT * KB_UNIT;
            } else if (value.endsWith(GIGABYTES)) {
                long numberPart = parseSizeWithUnit(value);
                size = numberPart * KB_UNIT * KB_UNIT * KB_UNIT;
            } else {
                size = Long.parseLong(value);
            }
            return ConversionService.SHARED.convert(size, targetType);
        } catch (NumberFormatException e) {
            context.reject(value, e);
            return Optional.empty();
        }
    }

    private long parseSizeWithUnit(String value) {
        return Long.parseLong(value.substring(0, value.length() - 2));
    }
}

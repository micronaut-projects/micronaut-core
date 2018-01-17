/*
 * Copyright 2017 original authors
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
package org.particleframework.runtime.converters.time;

import org.particleframework.context.annotation.Requires;
import org.particleframework.context.env.Environment;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverterRegistrar;
import org.particleframework.core.convert.format.Format;
import org.particleframework.core.util.StringUtils;

import javax.inject.Singleton;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registers data time converters
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
// Android doesn't support java.time
@Requires(notEnv = Environment.ANDROID)
public class TimeConverterRegistrar implements TypeConverterRegistrar{

    private static final Pattern DURATION_MATCHER = Pattern.compile("(\\d+)([s|m|h|d])(s?)");
    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                Duration.class,
                (object, targetType, context) -> {
                    String value = object.toString();
                    Matcher matcher = DURATION_MATCHER.matcher(value);
                    if(matcher.find()) {
                        String amount = matcher.group(1);
                        char type = matcher.group(2).charAt(0);
                        try {
                            switch (type) {
                                case 's':
                                    return Optional.of(Duration.ofSeconds(Integer.valueOf(amount)));
                                case 'm':
                                    String ms = matcher.group(3);
                                    if(StringUtils.hasText(ms)) {
                                        return Optional.of(Duration.ofMillis(Integer.valueOf(amount)));
                                    }
                                    else {
                                        return Optional.of(Duration.ofMinutes(Integer.valueOf(amount)));
                                    }
                                case 'h':
                                    return Optional.of(Duration.ofHours(Integer.valueOf(amount)));
                                case 'd':
                                    return Optional.of(Duration.ofDays(Integer.valueOf(amount)));
                            }
                        } catch (NumberFormatException e) {
                            context.reject(e);
                        }
                    }
                    return Optional.empty();
                }
        );

        // CharSequence -> LocalDataTime
        conversionService.addConverter(
                CharSequence.class,
                LocalDateTime.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = resolveFormatter(context);
                        LocalDateTime result = LocalDateTime.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );

        // CharSequence -> LocalDate
        conversionService.addConverter(
                CharSequence.class,
                LocalDate.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = resolveFormatter(context);
                        LocalDate result = LocalDate.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );


        // CharSequence -> ZonedDateTime
        conversionService.addConverter(
                CharSequence.class,
                ZonedDateTime.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = resolveFormatter(context);
                        ZonedDateTime result = ZonedDateTime.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );
    }

    private DateTimeFormatter resolveFormatter(ConversionContext context) {
        Format ann = context.getAnnotation(Format.class);
        Optional<String> format = ann != null ? Optional.of(ann.value()) : Optional.empty();
        return format
                .map((pattern) -> DateTimeFormatter.ofPattern(pattern, context.getLocale()))
                .orElse(DateTimeFormatter.RFC_1123_DATE_TIME);
    }
}

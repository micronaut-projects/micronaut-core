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
package io.micronaut.http.converters;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.HttpVersion;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URL;
import java.util.Optional;

/**
 * Converter registrar for HTTP classes.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public class SharedHttpConvertersRegistrar implements TypeConverterRegistrar {

    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(String.class, HttpVersion.class, s -> {
            try {
                return HttpVersion.valueOf(Double.parseDouble(s));
            } catch (NumberFormatException e) {
                return HttpVersion.valueOf(s);
            }
        });
        conversionService.addConverter(Number.class, HttpVersion.class, s -> HttpVersion.valueOf(s.doubleValue()));
        conversionService.addConverter(
                Number.class,
                HttpStatus.class,
                (object, targetType, context) -> {
                    try {
                        HttpStatus status = HttpStatus.valueOf(object.shortValue());
                        return Optional.of(status);
                    } catch (IllegalArgumentException e) {
                        context.reject(object, e);
                        return Optional.empty();
                    }
                }
        );
        conversionService.addConverter(
                CharSequence.class,
                SocketAddress.class,
                (object, targetType, context) -> {
                    String address = object.toString();
                    try {
                        URL url = new URL(address);
                        int port = url.getPort();
                        if (port == -1) {
                            port = url.getDefaultPort();
                        }
                        if (port == -1) {
                            context.reject(object, new ConfigurationException("Failed to find a port in the given value"));
                            return Optional.empty();
                        }
                        return Optional.of(InetSocketAddress.createUnresolved(url.getHost(), port));
                    } catch (MalformedURLException malformedURLException) {
                        String[] parts = object.toString().split(":");
                        if (parts.length == 2) {
                            try {
                                int port = Integer.parseInt(parts[1]);
                                return Optional.of(InetSocketAddress.createUnresolved(parts[0], port));
                            } catch (IllegalArgumentException illegalArgumentException) {
                                context.reject(object, illegalArgumentException);
                                return Optional.empty();
                            }
                        } else {
                            context.reject(object, new ConfigurationException("The address is not in a proper format of IP:PORT or a standard URL"));
                            return Optional.empty();
                        }
                    }
                }
        );
        conversionService.addConverter(
                CharSequence.class,
                ProxySelector.class,
                (object, targetType, context) -> {
                    if (object.toString().equals("default")) {
                        return Optional.of(ProxySelector.getDefault());
                    } else {
                        return Optional.empty();
                    }
                }
        );
    }
}

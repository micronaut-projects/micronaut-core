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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.Readable;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.simple.SimpleHttpHeaders;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Optional;

/**
 * Converter registrar for HTTP classes.
 *
 * @author graemerocher
 * @since 2.0
 */
@Prototype
public class HttpConverterRegistrar implements TypeConverterRegistrar {

    private final BeanProvider<ResourceResolver> resourceResolver;
    private final BeanProvider<MessageBodyHandlerRegistry> messageBodyHandlerRegistry;

    /**
     * Default constructor.
     *
     * @param resourceResolver The resource resolver
     * @param messageBodyHandlerRegistry Message body handler registry for file upload decoding
     */
    @Inject
    protected HttpConverterRegistrar(BeanProvider<ResourceResolver> resourceResolver, BeanProvider<MessageBodyHandlerRegistry> messageBodyHandlerRegistry) {
        this.resourceResolver = resourceResolver;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
    }

    @Override
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                Readable.class,
                (object, targetType, context) -> {
                    String pathStr = object.toString();
                    Optional<ResourceLoader> supportingLoader = resourceResolver.get().getSupportingLoader(pathStr);
                    if (supportingLoader.isEmpty()) {
                        context.reject(pathStr, new ConfigurationException(
                                "No supported resource loader for path [" + pathStr + "]. Prefix the path with a supported prefix such as 'classpath:' or 'file:'"
                        ));
                        return Optional.empty();
                    } else {
                        final Optional<URL> resource = resourceResolver.get().getResource(pathStr);
                        if (resource.isPresent()) {
                            return Optional.of(Readable.of(resource.get()));
                        } else {
                            context.reject(object, new ConfigurationException("No resource exists for value: " + object));
                            return Optional.empty();
                        }
                    }
                }
        );
        conversionService.addConverter(CompletedFileUpload.class, Object.class, (object, targetType, context) -> {
            Argument<Object> argument = context instanceof ArgumentConversionContext<?> ctx ? (Argument<Object>) ctx.getArgument() : Argument.of(targetType);
            try {
                if (argument.isAssignableFrom(InputStream.class)) {
                    return Optional.of(object.getInputStream());
                } else if (argument.isAssignableFrom(ReadBuffer.class)) {
                    if (!object.isInMemory()) {
                        context.reject(new IllegalStateException("File upload was stored on disk, refusing to copy it to memory for conversion to " + targetType));
                        return Optional.empty();
                    }
                    return Optional.of(object.toReadBuffer());
                } else if (byte[].class.equals(targetType)) {
                    // like InputStream and ReadBuffer, a raw view of the bytes: one copy out of the
                    // upload, rather than InputStream.readAllBytes growing an array through a reader
                    return Optional.of(object.getBytes());
                }

                MediaType mediaType = object.getContentType().orElse(null);
                Optional<MessageBodyReader<Object>> reader = messageBodyHandlerRegistry.get().findReader(argument, mediaType);
                if (reader.isPresent()) {
                    try (InputStream is = object.getInputStream()) {
                        return Optional.ofNullable(reader.get().read(argument, mediaType, new SimpleHttpHeaders(), is));
                    } catch (CodecException e) {
                        context.reject(e);
                        return Optional.empty();
                    }
                } else {
                    if (!object.isInMemory()) {
                        context.reject(new IllegalStateException("File upload was stored on disk, refusing to copy it to memory for conversion to " + targetType));
                        return Optional.empty();
                    }
                    try (ReadBuffer rb = object.toReadBuffer()) {
                        return conversionService.convert(rb, targetType, context);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        conversionService.addConverter(CompletedAttribute.class, Object.class, (object, targetType, context) -> {
            Argument<Object> argument = context instanceof ArgumentConversionContext<?> ctx ? (Argument<Object>) ctx.getArgument() : Argument.of(targetType);
            if (argument.isAssignableFrom(ReadBuffer.class)) {
                return Optional.of(object.toReadBuffer());
            } else if (argument.isAssignableFrom(InputStream.class)) {
                return Optional.of(object.getInputStream());
            } else {
                Charset declaredCharset = declaredCharset(object);
                try (ReadBuffer rb = object.toReadBuffer()) {
                    if (declaredCharset != null && !isBinary(targetType)) {
                        // The generic ReadBuffer conversion below would decode with the charset of
                        // the request, but this part declares one of its own. That applies to any
                        // textual target, including wrappers such as Optional<String>, so decode
                        // here and convert the text, rather than let the ReadBuffer conversion
                        // decode with the wrong charset on the way to the wrapper.
                        String s = rb.toString(declaredCharset);
                        if (targetType.isAssignableFrom(String.class)) {
                            return Optional.of(s);
                        }
                        return conversionService.convert(s, targetType, context);
                    }
                    Optional<Object> direct = conversionService.convert(rb, targetType, context);
                    // This detects Optional.empty and Optional[Optional.empty]
                    if (direct.isPresent() && (!targetType.equals(Optional.class) || ((Optional<?>) direct.get()).isPresent())) {
                        return direct;
                    }
                    String s = rb.toString(declaredCharset != null ? declaredCharset : context.getCharset());
                    if (targetType.isAssignableFrom(String.class)) {
                        return Optional.of(s);
                    }
                    return conversionService.convert(s, targetType, context);
                }
            }
        });
    }

    /**
     * Whether the given conversion target is a raw byte container, for which a part's declared
     * charset is irrelevant and decoding the bytes as text would corrupt them.
     */
    private static boolean isBinary(Class<?> targetType) {
        return targetType == byte[].class
            || java.nio.ByteBuffer.class.isAssignableFrom(targetType)
            || io.micronaut.core.io.buffer.ByteBuffer.class.isAssignableFrom(targetType)
            || ReadBuffer.class.isAssignableFrom(targetType)
            || InputStream.class.isAssignableFrom(targetType);
    }

    /**
     * Get the charset declared by the part itself. A multipart part may carry its own
     * {@code Content-Type} with a {@code charset} parameter, and that charset governs the value of
     * the part (RFC 7578, section 4.5), independently of the charset of the request.
     *
     * <p>The part metadata is client input and is not validated by the parser, so the declared
     * charset may be malformed or unknown to this JVM. Such a declaration is ignored and the
     * charset of the request is used instead, the same fallback that
     * {@link io.micronaut.http.util.HttpUtil#resolveCharset} applies to the charset of a
     * message.</p>
     *
     * @param part The part
     * @return The charset declared by the part, or {@code null} if it declares none or the one it
     * declares cannot be resolved
     */
    @Nullable
    private static Charset declaredCharset(CompletedPart part) {
        MediaType mediaType = part.getMetadata().mediaType();
        if (mediaType == null) {
            return null;
        }
        try {
            return mediaType.getCharset().orElse(null);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return null;
        }
    }
}

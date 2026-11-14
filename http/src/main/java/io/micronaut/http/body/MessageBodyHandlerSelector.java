/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.body;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A bean that chooses the {@link MessageBodyReader} or {@link MessageBodyWriter} of a lookup in
 * the default {@link MessageBodyHandlerRegistry}, instead of the first applicable candidate.
 *
 * <p>The registry passes every bean candidate whose declared type and media types match the
 * lookup, in its default order (candidates with a matching {@code @Consumes} / {@code @Produces}
 * first), <em>before</em> asking {@code isReadable} / {@code isWriteable}. The registration gives
 * access to the bean definition (type arguments, annotation metadata), so a selector can apply its
 * own ranking. The returned handler is cached per type and media types like the default choice;
 * a selector that needs to decide per value may return a handler that delegates.</p>
 *
 * <p>Selectors are consulted in order; the first non-null result wins. When every selector returns
 * {@code null}, the registry picks the first candidate that is readable / writeable for one of the
 * media types.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface MessageBodyHandlerSelector {

    /**
     * Select the reader of a lookup.
     *
     * @param type       The type to read
     * @param mediaTypes The media types of the lookup (including the additional types of codec configurations)
     * @param candidates The candidate readers in default order
     * @param <T>        The type
     * @return The reader, or {@code null} to leave the choice to the next selector or the registry
     */
    default <T> @Nullable MessageBodyReader<T> selectReader(Argument<T> type,
                                                            List<MediaType> mediaTypes,
                                                            List<BeanRegistration<MessageBodyReader<T>>> candidates) {
        return null;
    }

    /**
     * Select the writer of a lookup.
     *
     * @param type       The type to write
     * @param mediaTypes The media types of the lookup (including the additional types of codec configurations)
     * @param candidates The candidate writers in default order
     * @param <T>        The type
     * @return The writer, or {@code null} to leave the choice to the next selector or the registry
     */
    default <T> @Nullable MessageBodyWriter<T> selectWriter(Argument<T> type,
                                                            List<MediaType> mediaTypes,
                                                            List<BeanRegistration<MessageBodyWriter<T>>> candidates) {
        return null;
    }
}

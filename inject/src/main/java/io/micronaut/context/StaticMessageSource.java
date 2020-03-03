/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context;

import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A static mutable message source.
 *
 * @author graemerocher
 * @since 1.2
 */
public class StaticMessageSource extends AbstractMessageSource {

    private final Map<MessageKey, String> messageMap = new ConcurrentHashMap<>(40);

    /**
     * Adds a message to the default locale.
     * @param code The code
     * @param message The the message
     * @return This message source
     */
    public @NonNull StaticMessageSource addMessage(@NonNull String code, @NonNull String message) {
        if (StringUtils.isNotEmpty(code) && StringUtils.isNotEmpty(message)) {
            messageMap.put(new MessageKey(Locale.getDefault(), code), message);
        }
        return this;
    }

    /**
     * Adds a message to the default locale.
     * @param locale The locale
     * @param code The code
     * @param message The the message
     * @return This message source
     */
    public @NonNull StaticMessageSource addMessage(@NonNull Locale locale, @NonNull String code, @NonNull String message) {
        ArgumentUtils.requireNonNull("locale", locale);
        if (StringUtils.isNotEmpty(code) && StringUtils.isNotEmpty(message)) {
            messageMap.put(new MessageKey(locale, code), message);
        }
        return this;
    }

    @NonNull
    @Override
    public Optional<String> getRawMessage(@NonNull String code, @NonNull MessageContext context) {
        ArgumentUtils.requireNonNull("code", code);
        ArgumentUtils.requireNonNull("context", context);
        final String msg = messageMap.get(new MessageKey(context.getLocale(), code));
        if (msg != null) {
            return Optional.of(
                    msg
            );
        } else {
            return Optional.ofNullable(messageMap.get(new MessageKey(Locale.getDefault(), code)));
        }
    }

}

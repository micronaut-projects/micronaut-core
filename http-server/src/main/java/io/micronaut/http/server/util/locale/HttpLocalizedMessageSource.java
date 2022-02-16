/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.util.locale;

import io.micronaut.context.AbstractLocalizedMessageSource;
import io.micronaut.context.MessageSource;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.LocaleResolver;
import io.micronaut.http.HttpRequest;
import io.micronaut.runtime.http.scope.RequestAware;
import io.micronaut.runtime.http.scope.RequestScope;
import java.util.Locale;

/**
 * A {@link RequestScope} which uses the current {@link HttpRequest} to resolve the locale and hence return the localized messages.
 * @author Sergio del Amo
 * @since 3.4.0
 */
@RequestScope
public class HttpLocalizedMessageSource extends AbstractLocalizedMessageSource<HttpRequest<?>> implements RequestAware {
    private Locale locale;
    
    /**
     * @param localeResolver The locale resolver
     * @param messageSource  The message source
     */
    public HttpLocalizedMessageSource(LocaleResolver<HttpRequest<?>> localeResolver, MessageSource messageSource) {
        super(localeResolver, messageSource);
    }

    @Override
    @NonNull
    protected Locale getLocale() {
        if (locale == null) {
            throw new IllegalStateException("RequestAware::setRequest should have set the locale");
        }
        return locale;
    }

    @Override
    public void setRequest(HttpRequest<?> request) {
        this.locale = resolveLocale(request);
    }
}

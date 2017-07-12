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
package org.particleframework.http;

import java.util.Locale;

/**
 * Common interface for HTTP messages
 *
 * @param <B> The body type
 *
 * @see HttpRequest
 * @see HttpResponse
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpMessage<B> {
    /**
     * @return The {@link HttpHeaders} object
     */
    HttpHeaders getHeaders();

    /**
     * @return The HTTP message body
     */
    B getBody();

    /**
     * @return The locale of the message
     */
    default Locale getLocale() {
        return getHeaders().findFirst(HttpHeaders.CONTENT_LANGUAGE)
                .map(Locale::new)
                .orElse(null);
    }

    /**
     * The request or response content type
     * @return The content type
     */
    default MediaType getContentType() {
        return getHeaders().findFirst(HttpHeaders.CONTENT_TYPE)
                .map(MediaType::new)
                .orElse(null);
    }

}

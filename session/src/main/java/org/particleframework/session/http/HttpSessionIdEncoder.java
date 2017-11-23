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
package org.particleframework.session.http;

import org.particleframework.http.HttpRequest;
import org.particleframework.http.MutableHttpResponse;
import org.particleframework.session.Session;

/**
 * Strategy interface for encoding {@link Session} IDs so they are represented in the response
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpSessionIdEncoder {

    /**
     * Encode the given Session into the response. The strategy can choose to use headers, cookies or whatever strategy suites the use case
     *
     * @param request The request
     * @param response The response
     * @param session The session
     */
    void encodeId(HttpRequest<?> request,
                  MutableHttpResponse<?> response,
                  Session session);
}

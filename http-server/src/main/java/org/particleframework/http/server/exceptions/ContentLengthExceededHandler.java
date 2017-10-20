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
package org.particleframework.http.server.exceptions;

import org.particleframework.context.annotation.Primary;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.exceptions.ContentLengthExceededException;

import javax.inject.Singleton;

/**
 * Default handle for {@link ContentLengthExceededException} errors
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
public class ContentLengthExceededHandler implements ExceptionHandler<ContentLengthExceededException, Object>{
    @Override
    public Object handle(HttpRequest request, ContentLengthExceededException exception) {
        return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE);
    }
}


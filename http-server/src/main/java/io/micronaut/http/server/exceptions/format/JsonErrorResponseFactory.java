/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server.exceptions.format;

import io.micronaut.http.HttpRequest;

import java.util.Collections;
import java.util.List;

/**
 * Creates objects to be rendered as JSON that represent errors.
 *
 * @author James Kleeh
 * @since 2.4.0
 */
public interface JsonErrorResponseFactory {

    Object createResponse(HttpRequest<?> request,
                          Throwable cause,
                          List<Error> errorDetails);

    default Object createResponse(HttpRequest<?> request,
                                  Throwable cause,
                                  Error error) {
        return createResponse(request, cause, Collections.singletonList(error));
    }

    default Object createResponse(HttpRequest<?> request,
                                  Throwable cause,
                                  String message) {
        return createResponse(request, cause, Error.forMessage(message));
    }

}

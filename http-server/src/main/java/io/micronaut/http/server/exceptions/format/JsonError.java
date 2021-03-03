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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Contains information about an error that occurred.
 *
 * @author James Kleeh
 * @since 2.4.0
 */
public interface JsonError {

    default Optional<String> getPath() {
        return Optional.empty();
    }

    String getMessage();

    default Optional<String> getTitle() {
        return Optional.empty();
    }

    static List<JsonError> forMessage(String message) {
        return Collections.singletonList(() -> message);
    }

    static List<JsonError> forMessages(List<String> messages) {
        return messages.stream().map(msg -> (JsonError) () -> msg).collect(Collectors.toList());
    }
}

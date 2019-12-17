/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.context;

import java.util.Optional;

/**
 * A contract for providing a context path.
 *
 * @author James Kleeh
 * @since 1.2.6
 * @deprecated Use either {@link ClientContextPathProvider} or {@link ServerContextPathProvider} instead.
 */
@Deprecated
public interface ContextPathProvider {

    /**
     * @return An optional context path
     */
    Optional<String> getContextPath();

}

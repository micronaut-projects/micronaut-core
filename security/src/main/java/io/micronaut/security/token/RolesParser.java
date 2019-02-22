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
package io.micronaut.security.token;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * Parse roles from token claims.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public interface RolesParser {

    /**
     *
     * @param claims The claims of the token.
     * @return The granted roles.
     */
    List<String> parseRoles (@Nonnull Map<String, Object> claims);
}

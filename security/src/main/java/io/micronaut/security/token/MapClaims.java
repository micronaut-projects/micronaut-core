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
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A {@link Claims} implementation backed by a {@link Map}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public class MapClaims implements Claims {

    private final Map<String, Object> map;

    /**
     * Creates a MapClaims with the corresponding backing map.
     *
     * @param m is the backing map
     */
    public MapClaims(Map<String, Object> m) {
        this.map = m;
    }

    @Nullable
    @Override
    public Object get(String claimName) {
        return map.get(claimName);
    }

    @Nonnull
    @Override
    public Set<String> names() {
        return Collections.unmodifiableSet(map.keySet());
    }

    @Override
    public boolean contains(String claimName) {
        return map.containsKey(claimName);
    }
}

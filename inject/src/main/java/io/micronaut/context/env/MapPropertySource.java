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
package io.micronaut.context.env;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * A {@link PropertySource} that uses a map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MapPropertySource implements PropertySource {

    private final String name;
    private final Map map;

    /**
     * Creates a map property source.
     *
     * @param name The name of the property source
     * @param map  The map
     */
    public MapPropertySource(String name, Map map) {
        this.name = name;
        this.map = map;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object get(String key) {
        return map.get(key);
    }

    @Override
    public Iterator<String> iterator() {
        Iterator i = map.keySet().iterator();

        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public String next() {
                return i.next().toString();
            }
        };
    }

    /**
     * The backing map (unmodifiable).
     *
     * @return The backing map
     */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(map);
    }

    /**
     * Create a new {@link MapPropertySource} from the given map.
     *
     * @param name The name of the property source
     * @param map  The map
     * @return The map property source
     */
    public static MapPropertySource of(String name, Map<String, Object> map) {
        return new MapPropertySource(name, map);
    }

    @Override
    public String toString() {
        return getName();
    }
}

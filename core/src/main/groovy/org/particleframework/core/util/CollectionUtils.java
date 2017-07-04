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
package org.particleframework.core.util;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * <p>Utility methods for working with {@link java.util.Collection} types</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class CollectionUtils {
    /**
     * <p>Attempts to convert a collection to the given iterabable type</p>
     *
     * @param iterableType The iterable type
     * @param collection The collection
     * @return An {@link Optional} of the converted type
     */
    public static <T> Optional<Iterable<T>> convertCollection(Class<? extends Iterable<T>> iterableType, Collection<T> collection)  {
        if (iterableType.isInstance(collection)) {
            return Optional.of(collection);
        }
        if (iterableType.equals(Set.class)) {
            return Optional.of(new HashSet<>(collection));
        } else if (iterableType.equals(Queue.class)) {
            return Optional.of(new LinkedList<>(collection));
        } else if (iterableType.equals(List.class)) {
            return Optional.of(new ArrayList<>(collection));
        } else if (!iterableType.isInterface()) {
            try {
                Constructor<? extends Iterable<T>> constructor = iterableType.getConstructor(Collection.class);
                return Optional.of(constructor.newInstance(collection));
            } catch (Throwable e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * Create a {@link LinkedHashMap} from an array of values
     *
     * @param values The values
     * @return The created map
     */
    public static Map createMap(Object... values) {
        int len = values.length;
        if(len % 2 != 0) throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");

        Map answer = new LinkedHashMap(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            answer.put(values[i++], values[i++]);
        }
        return answer;
    }
}

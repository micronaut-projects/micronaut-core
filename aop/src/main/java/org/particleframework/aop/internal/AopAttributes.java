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
package org.particleframework.aop.internal;

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.MutableConvertibleValues;
import org.particleframework.core.convert.MutableConvertibleValuesMap;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is used for AOP infrastructure purposes and should not be used in user code.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AopAttributes {
    private static final ThreadLocal<Map<MethodKey, Entry>> attributes = new ThreadLocal<>();
    private AopAttributes() {
    }

    @Internal
    protected static MutableConvertibleValues get(Class declaringType, String method, Class...argTypes) {
        Map<MethodKey, Entry> map = attributes.get();
        if(map == null) {
            map = new ConcurrentHashMap<>();
            attributes.set(map);
        }
        MethodKey key = new MethodKey(declaringType, method, argTypes);
        Entry entry = map.get(key);
        MutableConvertibleValues attributes;
        if(entry == null) {
            entry = new Entry();
            attributes = new MutableConvertibleValuesMap();
            entry.values = attributes;
            map.put(key, entry);
        }
        else {
            entry.nestingCount++;
        }
        attributes = entry.values;
        return attributes;
    }

    @Internal
    protected static void remove(Class declaringType, String method, Class... argTypes) {
        Map<MethodKey, Entry> map = attributes.get();
        if(map != null) {
            MethodKey key = new MethodKey(declaringType, method, argTypes);
            Entry values = map.get(key);
            if(values != null) {
                if(values.nestingCount == 0) {
                    map.remove(key);
                }
                else {
                    values.nestingCount--;
                }
            }

            if(map.isEmpty()) {
                attributes.remove();
            }
        }
    }

    private static final class Entry {
        MutableConvertibleValues values;
        int nestingCount;
    }

    private static final class MethodKey {
        private final Class declaringType;
        private final String method;
        private final Class[] argumentTypes;

        public MethodKey(Class declaringType, String method, Class... argumentTypes) {
            this.declaringType = declaringType;
            this.method = method;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public int hashCode() {
            int result = declaringType.getName().hashCode();
            result = 31 * result + method.hashCode();
            if(argumentTypes != null) {
                for (Class element : argumentTypes)
                    result = 31 * result + (element == null ? 0 : element.getName().hashCode());

            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodKey methodKey = (MethodKey) o;

            if (!declaringType.equals(methodKey.declaringType)) return false;
            if (!method.equals(methodKey.method)) return false;
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            return Arrays.equals(argumentTypes, methodKey.argumentTypes);
        }
    }
}

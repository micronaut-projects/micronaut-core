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
package io.micronaut.cli.config

import groovy.transform.Canonical
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.yaml.snakeyaml.Yaml

/**
 * A {@link ConfigMap} implementation used at codegen time
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@CompileStatic
@Canonical
class CodeGenConfig implements Cloneable, ConfigMap {
    final NavigableMap configMap

    CodeGenConfig() {
        configMap = new NavigableMap()
    }

    CodeGenConfig(CodeGenConfig copyOf) {
        this((Map<String, Object>) copyOf.configMap)
    }

    CodeGenConfig(Map<String, Object> copyOf) {
        this()
        mergeMap(copyOf)
    }

    CodeGenConfig clone() {
        new CodeGenConfig(this)
    }

    @Override
    int size() {
        configMap.size()
    }

    @Override
    boolean isEmpty() {
        configMap.isEmpty()
    }

    @Override
    boolean containsKey(Object key) {
        configMap.containsKey(key)
    }

    @Override
    boolean containsValue(Object value) {
        configMap.containsValue(value)
    }

    @Override
    Object get(Object key) {
        configMap.get(key)
    }

    @Override
    Object put(String key, Object value) {
        configMap.put(key, value)
    }

    @Override
    Object remove(Object key) {
        throw new UnsupportedOperationException("Config cannot be mutated")
    }

    @Override
    void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException("Config cannot be mutated")
    }

    @Override
    void clear() {
        throw new UnsupportedOperationException("Config cannot be mutated")
    }

    @Override
    Set<String> keySet() {
        return configMap.keySet()
    }

    @Override
    Collection<Object> values() {
        return configMap.values()
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        configMap.entrySet()
    }

    @Override
    def <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        def value = getProperty(key, targetType)
        if (value == null) {
            throw new IllegalStateException("Property [$key] not found")
        }
        return value
    }

    void loadYml(File ymlFile) {
        ymlFile.withInputStream { InputStream input ->
            loadYml(input)
        }
    }

    @CompileDynamic
    // fails with CompileStatic!
    void loadYml(InputStream input) {
        Yaml yaml = new Yaml()
        for (Object yamlObject : yaml.loadAll(input)) {
            if (yamlObject instanceof Map) { // problem here with CompileStatic
                mergeMap((Map) yamlObject)
            }
        }
    }

    void mergeMap(Map sourceMap, boolean parseFlatKeys = false) {
        configMap.merge(sourceMap, parseFlatKeys)
    }

    public <T> T navigate(Class<T> requiredType, String... path) {
        Object result = configMap.navigate(path)
        if (result == null) {
            return null
        }
        return convertToType(result, requiredType)
    }

    protected <T> T convertToType(Object value, Class<T> requiredType) {
        if (value == null || value instanceof NavigableMap.NullSafeNavigator) {
            return null
        } else if (requiredType.isInstance(value)) {
            return (T) value
        }
        if (requiredType == String.class) {
            return String.valueOf(value)
        } else if (requiredType == Boolean.class) {
            Boolean booleanObject = toBooleanObject(String.valueOf(value))
            return booleanObject != null ? booleanObject : Boolean.FALSE
        } else if (requiredType == boolean) {
            Boolean booleanObject = toBooleanObject(String.valueOf(value))
            return booleanObject != null ? booleanObject.booleanValue() : Boolean.FALSE.booleanValue()
        } else if (requiredType == Integer.class) {
            if (value instanceof Number) {
                return Integer.valueOf(((Number) value).intValue())
            } else {
                return Integer.valueOf(String.valueOf(value))
            }
        } else if (requiredType == Long.class) {
            if (value instanceof Number) {
                return Long.valueOf(((Number) value).longValue())
            } else {
                return Long.valueOf(String.valueOf(value))
            }
        } else if (requiredType == Double.class) {
            if (value instanceof Number) {
                return Double.valueOf(((Number) value).doubleValue())
            } else {
                return Double.valueOf(String.valueOf(value))
            }
        } else if (requiredType == BigDecimal.class) {
            return new BigDecimal(String.valueOf(value))
        } else {
            return convertToOtherTypes(value, requiredType)
        }
    }

    protected <T> T convertToOtherTypes(Object value, Class<T> requiredType) {
        throw new RuntimeException("conversion of $value to $requiredType.name not implemented")
    }

    public Object navigate(String... path) {
        return navigate(Object, path)
    }

    public boolean asBoolean() {
        return !configMap.isEmpty()
    }

    public Object asType(Class type) {
        if (type == Boolean || type == boolean) {
            return asBoolean()
        } else if (type == String) {
            return toString()
        } else if (type == Map) {
            return this
        } else if (type == CodeGenConfig) {
            return new CodeGenConfig(this)
        } else {
            throw new GroovyCastException(this, type)
        }
    }

    public Object getAt(Object key) {
        getProperty(String.valueOf(key))
    }

    public void setAt(Object key, Object value) {
        setProperty(String.valueOf(key), value)
    }

    public Object getProperty(String name) {
        if ("configMap".equals(name))
            return this.configMap
        return configMap.getProperty(name)
    }

    public Object get(String name) {
        if ("configMap".equals(name))
            return this.configMap
        return configMap.getProperty(name)
    }

    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return DefaultGroovyMethods.iterator(configMap);
    }

    public <T> T getProperty(String name, Class<T> requiredType) {
        return convertToType(configMap.getProperty(name), requiredType)
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        def v = getProperty(key, targetType)
        if (v == null) {
            return defaultValue
        }
        return v
    }

    public void setProperty(String name, Object value) {
        configMap.setProperty(name, value)
    }

    /**
     * toBooleanObject method ported from org.apache.commons.lang.BooleanUtils.toBooleanObject to Groovy code
     * @param str
     * @return
     */
    private static Boolean toBooleanObject(String str) {
        if (str.is("true")) {
            return Boolean.TRUE
        }
        if (str == null) {
            return null
        }
        int strlen = str.length()
        if (strlen == 0) {
            return null
        } else if (strlen == 1) {
            char ch0 = str.charAt(0)
            if ((ch0 == 'y' || ch0 == 'Y') ||
                (ch0 == 't' || ch0 == 'T')) {
                return Boolean.TRUE
            }
            if ((ch0 == 'n' || ch0 == 'N') ||
                (ch0 == 'f' || ch0 == 'F')) {
                return Boolean.FALSE
            }
        } else if (strlen == 2) {
            char ch0 = str.charAt(0)
            char ch1 = str.charAt(1)
            if ((ch0 == 'o' || ch0 == 'O') &&
                (ch1 == 'n' || ch1 == 'N')) {
                return Boolean.TRUE
            }
            if ((ch0 == 'n' || ch0 == 'N') &&
                (ch1 == 'o' || ch1 == 'O')) {
                return Boolean.FALSE
            }
        } else if (strlen == 3) {
            char ch0 = str.charAt(0)
            char ch1 = str.charAt(1)
            char ch2 = str.charAt(2)
            if ((ch0 == 'y' || ch0 == 'Y') &&
                (ch1 == 'e' || ch1 == 'E') &&
                (ch2 == 's' || ch2 == 'S')) {
                return Boolean.TRUE
            }
            if ((ch0 == 'o' || ch0 == 'O') &&
                (ch1 == 'f' || ch1 == 'F') &&
                (ch2 == 'f' || ch2 == 'F')) {
                return Boolean.FALSE
            }
        } else if (strlen == 4) {
            char ch0 = str.charAt(0)
            char ch1 = str.charAt(1)
            char ch2 = str.charAt(2)
            char ch3 = str.charAt(3)
            if ((ch0 == 't' || ch0 == 'T') &&
                (ch1 == 'r' || ch1 == 'R') &&
                (ch2 == 'u' || ch2 == 'U') &&
                (ch3 == 'e' || ch3 == 'E')) {
                return Boolean.TRUE
            }
        } else if (strlen == 5) {
            char ch0 = str.charAt(0)
            char ch1 = str.charAt(1)
            char ch2 = str.charAt(2)
            char ch3 = str.charAt(3)
            char ch4 = str.charAt(4)
            if ((ch0 == 'f' || ch0 == 'F') &&
                (ch1 == 'a' || ch1 == 'A') &&
                (ch2 == 'l' || ch2 == 'L') &&
                (ch3 == 's' || ch3 == 'S') &&
                (ch4 == 'e' || ch4 == 'E')) {
                return Boolean.FALSE
            }
        }
        return null
    }
}

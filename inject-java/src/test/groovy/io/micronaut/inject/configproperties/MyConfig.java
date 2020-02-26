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
package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.ReadableBytes;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties("foo.bar")
public class MyConfig {
    int port;
    Integer defaultValue = 9999;
    int primitiveDefaultValue = 9999;
    List<String> stringList;
    List<Integer> intList;
    List<URL> urlList;
    List<URL> urlList2;
    List<URL> emptyList;
    Map<String,Integer> flags;
    Optional<URL> url;
    Optional<URL> anotherUrl = Optional.empty();
    Inner inner;
    protected int defaultPort = 9999;
    protected Integer anotherPort;

    private int maxSize;
    @ReadableBytes
    int anotherSize;


    private Map<String, Map<String, Value>> map = new HashMap<>();

    public void setMap(Map<String, Map<String, Value>> map) {
        this.map = map;
    }

    public Map<String, Map<String, Value>> getMap() {
        return map;
    }

    public static class Value {
        private int property;
        private Value2 property2;

        public Value() {
        }

        public Value(int property, Value2 property2) {
            this.property = property;
            this.property2 = property2;
        }

        public int getProperty() {
            return property;
        }

        public void setProperty(int property) {
            this.property = property;
        }

        public Value2 getProperty2() {
            return property2;
        }

        public void setProperty2(Value2 property2) {
            this.property2 = property2;
        }
    }

    public static class Value2 {
        private int property;

        public Value2() {
        }

        public Value2(int property) {
            this.property = property;
        }

        public int getProperty() {
            return property;
        }

        public void setProperty(int property) {
            this.property = property;
        }
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(@ReadableBytes int maxSize) {
        this.maxSize = maxSize;
    }

    public int getAnotherSize() {
        return anotherSize;
    }

    public Integer getAnotherPort() {
        return anotherPort;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    @ConfigurationProperties("inner")
    public static class Inner {
        boolean enabled;

        public boolean getEnabled() {
            return enabled;
        }
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Integer getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Integer defaultValue) {
        this.defaultValue = defaultValue;
    }

    public int getPrimitiveDefaultValue() {
        return primitiveDefaultValue;
    }

    public void setPrimitiveDefaultValue(int primitiveDefaultValue) {
        this.primitiveDefaultValue = primitiveDefaultValue;
    }

    public List<String> getStringList() {
        return stringList;
    }

    public void setStringList(List<String> stringList) {
        this.stringList = stringList;
    }

    public List<Integer> getIntList() {
        return intList;
    }

    public void setIntList(List<Integer> intList) {
        this.intList = intList;
    }

    public List<URL> getUrlList() {
        return urlList;
    }

    public void setUrlList(List<URL> urlList) {
        this.urlList = urlList;
    }

    public List<URL> getUrlList2() {
        return urlList2;
    }

    public void setUrlList2(List<URL> urlList2) {
        this.urlList2 = urlList2;
    }

    public List<URL> getEmptyList() {
        return emptyList;
    }

    public void setEmptyList(List<URL> emptyList) {
        this.emptyList = emptyList;
    }

    public Map<String, Integer> getFlags() {
        return flags;
    }

    public void setFlags(Map<String, Integer> flags) {
        this.flags = flags;
    }

    public Optional<URL> getUrl() {
        return url;
    }

    public void setUrl(Optional<URL> url) {
        this.url = url;
    }

    public Optional<URL> getAnotherUrl() {
        return anotherUrl;
    }

    public void setAnotherUrl(Optional<URL> anotherUrl) {
        this.anotherUrl = anotherUrl;
    }

    public Inner getInner() {
        return inner;
    }

    public void setInner(Inner inner) {
        this.inner = inner;
    }
}

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
package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties('foo.bar')
class MyConfig {
    int port
    Integer defaultValue = 9999
    int primitiveDefaultValue = 9999
    protected int defaultPort = 9999
    protected Integer anotherPort
    List<String> stringList
    List<Integer> intList
    List<URL> urlList
    List<URL> urlList2
    List<URL> emptyList
    Map<String,Integer> flags
    Optional<URL> url
    Optional<URL> anotherUrl = Optional.empty()
    Inner inner

    Integer getAnotherPort() {
        return anotherPort
    }

    int getDefaultPort() {
        return defaultPort
    }

    @ConfigurationProperties('inner')
    static class Inner {
        boolean enabled
    }
}

package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
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

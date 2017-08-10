package org.particleframework.inject.configproperties;

import org.particleframework.config.ConfigurationProperties;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties("foo.bar")
public class MyConfig {
    int port;
    Integer defaultValue = 9999;
    int primitiveDefaultValue = 9999;
    protected int defaultPort = 9999;
    protected Integer anotherPort;
    List<String> stringList;
    List<Integer> intList;
    List<URL> urlList;
    List<URL> urlList2;
    List<URL> emptyList;
    Map<String,Integer> flags;
    Optional<URL> url;
    Optional<URL> anotherUrl;
    Inner inner;

    public Integer getAnotherPort() {
        return anotherPort;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    static class Inner {
        boolean enabled;

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

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public void setAnotherPort(Integer anotherPort) {
        this.anotherPort = anotherPort;
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
}

package io.micronaut.inject.configproperties;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

@Requires(property = "spec", value = "ConfigurationPropertiesInjectSpec")
@ConfigurationProperties("foo.bar")
class MyConfigWithConstructorConfigurationInject {
    private String host;
    private int serverPort;
    private MI_OtherConfig otherConfig;
    private MI_OtherMissingConfig otherMissingConfig;
    private MI_OtherBean otherBean;
    private MI_OtherSingleton otherSingleton;
    private Optional<MI_OtherSingleton> optionalOtherSingleton;
    private BeanProvider<MI_OtherSingleton> otherSingletonBeanProvider;
    private Provider<MI_OtherSingleton> otherSingletonProvider;

    @ConfigurationInject
    MyConfigWithConstructorConfigurationInject(String host,
                                               int serverPort,
                                               MI_OtherConfig otherConfig,
                                               MI_OtherMissingConfig otherMissingConfig,
                                               MI_OtherBean otherBean,
                                               MI_OtherSingleton otherSingleton,
                                               Optional<MI_OtherSingleton> optionalOtherSingleton,
                                               BeanProvider<MI_OtherSingleton> otherSingletonBeanProvider,
                                               Provider<MI_OtherSingleton> otherSingletonProvider) {
        this.host = host;
        this.serverPort = serverPort;
        this.otherConfig = otherConfig;
        this.otherMissingConfig = otherMissingConfig;
        this.otherBean = otherBean;
        this.otherSingleton = otherSingleton;
        this.optionalOtherSingleton = optionalOtherSingleton;
        this.otherSingletonBeanProvider = otherSingletonBeanProvider;
        this.otherSingletonProvider = otherSingletonProvider;
    }

    public String getHost() {
        return host;
    }

    public int getServerPort() {
        return serverPort;
    }

    public MI_OtherBean getOtherBean() {
        return otherBean;
    }

    public MI_OtherConfig getOtherConfig() {
        return otherConfig;
    }

    public MI_OtherMissingConfig getOtherMissingConfig() {
        return otherMissingConfig;
    }

    public MI_OtherSingleton getOtherSingleton() {
        return otherSingleton;
    }

    public Optional<MI_OtherSingleton> getOptionalOtherSingleton() {
        return optionalOtherSingleton;
    }

    public BeanProvider<MI_OtherSingleton> getOtherSingletonBeanProvider() {
        return otherSingletonBeanProvider;
    }

    public Provider<MI_OtherSingleton> getOtherSingletonProvider() {
        return otherSingletonProvider;
    }
}

@ConfigurationProperties("xyz")
class CI_OtherConfig {

    String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

@ConfigurationProperties("abc")
class CI_OtherMissingConfig {

    String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

@Bean
class CI_OtherBean {
}

@Singleton
class CI_OtherSingleton {
}

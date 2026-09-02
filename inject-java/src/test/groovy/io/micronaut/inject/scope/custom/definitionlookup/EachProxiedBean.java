package io.micronaut.inject.scope.custom.definitionlookup;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;

public class EachProxiedBean {

    public EachProxiedBean() {
    }

    public String hello() {
        return "hello";
    }
}

@EachProperty("proxies")
class EachProxyConfig {
    public EachProxyConfig(@Parameter String name) {
    }
}

@Factory
class EachProxyFactory {
    @EachBean(EachProxyConfig.class)
    @LookupProxyScope
    EachProxiedBean each(EachProxyConfig config) {
        return new EachProxiedBean();
    }
}

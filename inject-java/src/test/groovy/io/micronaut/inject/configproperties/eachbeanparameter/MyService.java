package io.micronaut.inject.configproperties.eachbeanparameter;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Requires(property = "spec", value = "EachBeanParameterSpec")
@Singleton
class MyService {

    private final MyBean defaultBean, fooBean, barBean;

    MyService(@Named("default") MyBean defaultBean, @Named("foo") MyBean fooBean, @Named("bar") MyBean barBean) {
        this.defaultBean = defaultBean;
        this.fooBean = fooBean;
        this.barBean = barBean;
   }

    public MyBean getDefaultBean() {
        return defaultBean;
    }

    public MyBean getFooBean() {
        return fooBean;
    }

    public MyBean getBarBean() {
        return barBean;
    }
}

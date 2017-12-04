package org.particleframework.inject.foreach;

import org.particleframework.context.annotation.EachBean;
import org.particleframework.context.annotation.EachProperty;

@EachBean(MyConfigurationWithPrimary.class)
public class MyBeanWithPrimary {
    final MyConfigurationWithPrimary configuration;

    MyBeanWithPrimary(MyConfigurationWithPrimary configuration) {
        this.configuration = configuration;
    }

    public MyConfigurationWithPrimary getConfiguration() {
        return configuration;
    }
}

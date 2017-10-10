package org.particleframework.inject.foreach;

import org.particleframework.context.annotation.ForEach;

@ForEach(MyConfigurationWithPrimary.class)
public class MyBeanWithPrimary {
    final MyConfigurationWithPrimary configuration;

    MyBeanWithPrimary(MyConfigurationWithPrimary configuration) {
        this.configuration = configuration;
    }

    public MyConfigurationWithPrimary getConfiguration() {
        return configuration;
    }
}

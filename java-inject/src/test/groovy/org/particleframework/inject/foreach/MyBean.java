package org.particleframework.inject.foreach;

import org.particleframework.context.annotation.ForEach;

@ForEach(MyConfiguration.class)
public class MyBean {
    final MyConfiguration configuration;

    MyBean(MyConfiguration configuration) {
        this.configuration = configuration;
    }

    public MyConfiguration getConfiguration() {
        return configuration;
    }
}

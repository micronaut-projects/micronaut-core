package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.inject.configproperties.other.ParentConfigProperties

@ConfigurationProperties("child")
class ChildConfigProperties extends ParentConfigProperties {

    protected void setName(String name) {
        super.setName(name)
    }

}

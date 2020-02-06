package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.order.Ordered

@EachProperty(value = "teams", list = true)
class ParentArrayEachProps implements Ordered {

    private final Integer index
    Integer wins
    ManagerProps manager

    ParentArrayEachProps(@Parameter Integer index) {
        this.index = index
    }

    @Override
    int getOrder() {
        index
    }

    @ConfigurationProperties("manager")
    static class ManagerProps implements Ordered {

        private final Integer index
        Integer age

        ManagerProps(@Parameter Integer index) {
            this.index = index
        }

        @Override
        int getOrder() {
            index
        }

    }
}

package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.order.Ordered

import javax.annotation.Nullable

@EachProperty(value = "teams", list = true)
class ParentArrayEachPropsCtor implements Ordered {

    Integer wins
    private final Integer index
    private ManagerProps managerProps

    ParentArrayEachPropsCtor(@Parameter Integer index, @Nullable ManagerProps managerProps) {
        this.index = index
        this.managerProps = managerProps
    }

    @Override
    int getOrder() {
        index
    }

    ManagerProps getManager() {
        managerProps
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

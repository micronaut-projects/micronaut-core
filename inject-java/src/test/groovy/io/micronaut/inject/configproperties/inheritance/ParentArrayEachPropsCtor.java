package io.micronaut.inject.configproperties.inheritance;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.order.Ordered;

import javax.annotation.Nullable;

@EachProperty(value = "teams", list = true)
public class ParentArrayEachPropsCtor implements Ordered {

    private Integer wins;
    private final Integer index;
    private ManagerProps managerProps;

    ParentArrayEachPropsCtor(@Parameter Integer index, @Nullable ManagerProps managerProps) {
        this.index = index;
        this.managerProps = managerProps;
    }

    @Override
    public int getOrder() {
        return index;
    }

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public ManagerProps getManager() {
        return managerProps;
    }

    @ConfigurationProperties("manager")
    public static class ManagerProps implements Ordered {

        private final Integer index;
        private Integer age;

        ManagerProps(@Parameter Integer index) {
            this.index = index;
        }

        @Override
        public int getOrder() {
            return index;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

    }
}

package io.micronaut.inject.configproperties.inheritance;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.order.Ordered;

@EachProperty(value = "teams", list = true)
public class ParentArrayEachProps implements Ordered {

    private final Integer index;
    private Integer wins;
    private ManagerProps managerProps;

    ParentArrayEachProps(@Parameter Integer index) {
        this.index = index;
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

    public void setManager(ManagerProps managerProps) {
        this.managerProps = managerProps;
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

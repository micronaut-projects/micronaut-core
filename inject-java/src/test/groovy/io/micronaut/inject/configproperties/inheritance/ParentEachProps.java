package io.micronaut.inject.configproperties.inheritance;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;

@EachProperty("teams")
public class ParentEachProps {

    private Integer wins;
    private ManagerProps managerProps;

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
    public static class ManagerProps {

        private Integer age;

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }
}

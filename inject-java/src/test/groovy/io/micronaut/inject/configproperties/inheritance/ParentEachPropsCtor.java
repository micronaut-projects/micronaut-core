package io.micronaut.inject.configproperties.inheritance;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import edu.umd.cs.findbugs.annotations.Nullable;

@EachProperty("teams")
public class ParentEachPropsCtor {

    private Integer wins;
    private final String name;
    private ManagerProps managerProps;

    ParentEachPropsCtor(@Parameter String name,
                        @Nullable ManagerProps managerProps) {
        this.name = name;
        this.managerProps = managerProps;
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

    public String getName() {
        return name;
    }

    @ConfigurationProperties("manager")
    public static class ManagerProps {

        private final String name;
        private Integer age;

        ManagerProps(@Parameter String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getName() {
            return name;
        }
    }
}

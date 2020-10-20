package io.micronaut.inject.visitor.beans;

public class OuterBean {

    public class InnerBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}

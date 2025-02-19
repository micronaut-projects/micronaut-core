package io.micronaut.inject.visitor.beans;

public class Message {

    public Builder<?> getBuilder() {
        return new Builder<>();
    }

    public static final class Builder<BuilderT extends Builder<BuilderT>> {

        class BuilderParentImpl {
        }

        private Builder<BuilderT>.BuilderParentImpl meAsParent;
    }
}

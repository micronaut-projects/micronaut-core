package io.micronaut.reflection;

/**
 * A method declared along one line of super classes: overridden at every level, but never in parallel branches.
 */
public class LinearOverride {

    public static class Grand {
        public void act(String value) {
        }
    }

    public static class Parent extends Grand {
        @Override
        public void act(String value) {
        }
    }

    public static class Leaf extends Parent {
        @Override
        public void act(String value) {
        }
    }
}

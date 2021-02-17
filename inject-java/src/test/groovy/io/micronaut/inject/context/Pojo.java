package io.micronaut.inject.context;

public class Pojo {

    public static boolean instantiated;

    public Pojo() {
        instantiated = true;
    }
}

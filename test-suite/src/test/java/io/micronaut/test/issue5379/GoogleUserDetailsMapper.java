package io.micronaut.test.issue5379;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.groovy.internal.util.Supplier;

import java.util.List;

@Singleton
@Named("google")
class GoogleUserDetailsMapper implements Supplier<String>, OAuth2Controller {
    private final AmazonDynamoDB amazonDynamoDB;

    GoogleUserDetailsMapper(AmazonDynamoDB amazonDynamoDB, SomeInterface someInterface) {
        this.amazonDynamoDB = amazonDynamoDB;
    }

    @Override
    public String get() {
        return amazonDynamoDB.get();
    }
}

@Singleton
class SomeConfigurationList {
    List<SomeConfiguration> controllers;

    public SomeConfigurationList(List<SomeConfiguration> controllers) {
        this.controllers = controllers;
    }
}

@EachProperty("test")
class SomeConfiguration {
    private OAuth2Controller controller;

    SomeConfiguration(@Parameter OAuth2Controller controller) {
        this.controller = controller;
    }
}

interface OAuth2Controller {

}

@Factory
class AmazonDynamoDBFactory {

    @Singleton
    AmazonDynamoDB amazonDynamoDB() {
        return () -> "good";
    }
}

interface AmazonDynamoDB extends Supplier<String> {}

interface SomeInterface {
    void test();
}

@Singleton
class SomeClassImplementation implements SomeInterface {
    @Override
    public void test() {
        // do stuff
    }
}

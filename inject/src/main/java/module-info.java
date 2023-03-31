module micronaut.inject {
    requires transitive jakarta.inject;//api
    requires transitive micronaut.core;
    requires transitive jakarta.annotation;

    requires static kotlin.stdlib;
    requires static javax.inject;
    requires static org.yaml.snakeyaml;
    requires static org.apache.groovy;
    requires static org.jetbrains.annotations;
    requires org.slf4j;

    exports io.micronaut.context;
    exports io.micronaut.context.annotation;
    exports io.micronaut.context.banner;
    exports io.micronaut.context.condition;
    exports io.micronaut.context.converters;
    exports io.micronaut.context.env;
    exports io.micronaut.context.event;
    exports io.micronaut.context.exceptions;
    exports io.micronaut.context.i18n;
    exports io.micronaut.context.processor;//these are just templates for processors not the actual processors
    exports io.micronaut.context.scope;

    exports io.micronaut.inject;
    exports io.micronaut.inject.annotation;
    exports io.micronaut.inject.beans;
    exports io.micronaut.inject.provider;
    exports io.micronaut.inject.proxy;
    exports io.micronaut.inject.qualifiers;
    exports io.micronaut.inject.validation;
}

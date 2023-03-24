module micronaut.aop {
    requires transitive micronaut.core;
    requires static micronaut.core.reactive;
    requires static kotlin.stdlib;
    requires micronaut.inject;
    requires jakarta.inject;
    requires org.reactivestreams;
    requires jakarta.annotation;
    requires org.slf4j;

    exports io.micronaut.aop;
    exports io.micronaut.aop.exceptions;
    exports io.micronaut.aop.chain;
    exports io.micronaut.aop.kotlin;
    exports io.micronaut.aop.util;
    exports io.micronaut.aop.internal.intercepted to micronaut.core.processor;
}

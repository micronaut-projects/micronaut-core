module micronaut.context {
    requires jakarta.inject;
    requires org.apache.logging.log4j.core;
    requires micronaut.core;
    requires micronaut.inject;
    requires org.apache.logging.log4j;
    requires jakarta.annotation;
    requires org.slf4j;
    requires micronaut.core.processor;
    requires micronaut.core.reactive;
    requires org.reactivestreams;
    exports io.micronaut.logging;
    exports io.micronaut.logging.impl;

    exports io.micronaut.runtime;

    exports io.micronaut.runtime.context;
    exports io.micronaut.runtime.context.env;
    exports io.micronaut.runtime.context.scope;
    exports io.micronaut.runtime.context.scope.refresh;

    exports io.micronaut.runtime.event;
    exports io.micronaut.runtime.event.annotation;

    exports io.micronaut.runtime.exceptions;

    exports io.micronaut.runtime.server;
    exports io.micronaut.runtime.server.event;
    exports io.micronaut.runtime.server.watch.event;

    exports io.micronaut.scheduling;
    exports io.micronaut.scheduling.annotation;
    exports io.micronaut.scheduling.async;
    exports io.micronaut.scheduling.async.validation;
    exports io.micronaut.scheduling.cron;
    exports io.micronaut.scheduling.exceptions;
    exports io.micronaut.scheduling.executor;
    exports io.micronaut.scheduling.instrument;
    exports io.micronaut.scheduling.io.watch;
    exports io.micronaut.scheduling.io.watch.event;
}

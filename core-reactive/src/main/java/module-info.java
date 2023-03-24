module micronaut.core.reactive {
    requires org.reactivestreams;
    requires micronaut.core;
    requires org.slf4j;
    exports io.micronaut.core.async.publisher;
}

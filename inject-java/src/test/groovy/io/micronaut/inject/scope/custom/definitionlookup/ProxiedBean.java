package io.micronaut.inject.scope.custom.definitionlookup;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@LookupProxyScope
public class ProxiedBean {

    public static int created;
    public static int destroyed;

    /**
     * Counted on post-construct rather than in the constructor: the generated proxy is a subclass and runs the
     * constructor too, but only the target gets its lifecycle hooks invoked.
     */
    @PostConstruct
    void init() {
        created++;
    }

    public String hello() {
        return "hello";
    }

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}

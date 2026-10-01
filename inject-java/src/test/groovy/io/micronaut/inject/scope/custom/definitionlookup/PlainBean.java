package io.micronaut.inject.scope.custom.definitionlookup;

import jakarta.annotation.PreDestroy;

@LookupScope
public class PlainBean {

    public static int created;
    public static int destroyed;

    public PlainBean() {
        created++;
    }

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}

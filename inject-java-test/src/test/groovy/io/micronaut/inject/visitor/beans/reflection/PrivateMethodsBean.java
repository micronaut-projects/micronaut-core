package io.micronaut.inject.visitor.beans.reflection;

import io.micronaut.core.annotation.Introspected;

@Introspected(accessKind = Introspected.AccessKind.METHOD, visibility = Introspected.Visibility.ANY)
class PrivateMethodsBean {
    private String name;

    private String getName() {
        return name;
    }

    private void setName(String name) {
        this.name = name;
    }
}

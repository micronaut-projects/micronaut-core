package io.micronaut.inject.visitor.beans.reflection;

import io.micronaut.core.annotation.Introspected;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class PrivateFieldBean2 {
    private int abc;
}

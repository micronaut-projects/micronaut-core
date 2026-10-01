package io.micronaut.inject.visitor.beans.outer;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.visitor.beans.hidden.HiddenChild;

/**
 * Introspects {@link HiddenChild} from another package: its super class is package-private there.
 */
@Introspected(classes = HiddenChild.class, accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY, members = true)
public class HiddenImport {
}

package io.micronaut.inject.annotation.internal;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * A remapper that remaps findbugs annotations to javax.annotation which are represented internally.
 *
 * @author graemerocher
 * @since 1.2.0
 */
@Internal
public final class FindBugsRemapper implements AnnotationRemapper {
    @Override
    @Nonnull public String getPackageName() {
        return "edu.umd.cs.findbugs.annotations";
    }

    @Override
    @Nonnull public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        String simpleName = NameUtils.getSimpleName(annotation.getAnnotationName());
        if ("nullable".equalsIgnoreCase(simpleName)) {
            return Collections.singletonList(
                    AnnotationValue.builder(AnnotationUtil.NULLABLE).build()
            );
        } else if ("nonnull".equalsIgnoreCase(simpleName)) {
            return Collections.singletonList(
                    AnnotationValue.builder(AnnotationUtil.NON_NULL).build()
            );
        }
        return Collections.singletonList(annotation);
    }
}

package io.micronaut.inject.annotation.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Internal
public final class JakartaRemapper implements AnnotationRemapper {

    private static final Pattern JAKARTA = Pattern.compile("^jakarta");

    @Override
    @NonNull
    public String getPackageName() {
        return "jakarta.inject";
    }

    @Override
    @NonNull public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        String name = annotation.getAnnotationName();
        Matcher matcher = JAKARTA.matcher(name);

        return Collections.singletonList(
                AnnotationValue.builder(matcher.replaceFirst("javax")).members(annotation.getValues()).build()
        );
    }
}

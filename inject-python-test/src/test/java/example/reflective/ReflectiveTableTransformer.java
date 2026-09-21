package example.reflective;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Adds to every {@link ReflectiveTable} the member {@code schema}, which the annotation type does not declare,
 * and replaces the {@code catalog} default with a value that cannot be written as a String constant. A table
 * named {@code broken} gets such a value for its {@code name} as well, which has no default to fall back to.
 */
public class ReflectiveTableTransformer implements TypedAnnotationTransformer<ReflectiveTable> {

    private static final String SCHEMA_MEMBER = "schema";
    private static final String SCHEMA = "public";
    private static final String BROKEN_TABLE = "broken";
    private static final String NAME_MEMBER = "name";
    private static final int NOT_A_STRING = 42;

    @Override
    public Class<ReflectiveTable> annotationType() {
        return ReflectiveTable.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<ReflectiveTable> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<ReflectiveTable> builder = annotation.mutate()
            .member(SCHEMA_MEMBER, SCHEMA)
            .member("catalog", NOT_A_STRING);
        if (annotation.stringValue(NAME_MEMBER).filter(BROKEN_TABLE::equals).isPresent()) {
            builder.member(NAME_MEMBER, NOT_A_STRING);
        }
        return List.of(builder.build());
    }
}

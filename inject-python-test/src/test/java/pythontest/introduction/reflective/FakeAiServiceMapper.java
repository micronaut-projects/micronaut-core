package pythontest.introduction.reflective;

import io.micronaut.core.annotation.AllowsReflection;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Maps {@link FakeAiService} to the {@link AllowsReflection} hint: the generated Java interface of a
 * Python class annotated with it carries its runtime annotations without any compiler option.
 */
public class FakeAiServiceMapper implements TypedAnnotationMapper<FakeAiService> {

    @Override
    public Class<FakeAiService> annotationType() {
        return FakeAiService.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<FakeAiService> annotation, VisitorContext visitorContext) {
        return List.of(AnnotationValue.builder(AllowsReflection.class).build());
    }
}

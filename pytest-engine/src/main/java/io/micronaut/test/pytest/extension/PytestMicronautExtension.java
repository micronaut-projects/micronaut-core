package io.micronaut.test.pytest.extension;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.test.annotation.MicronautTestValue;
import io.micronaut.test.extensions.AbstractMicronautExtension;
import org.graalvm.polyglot.Value;

import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Map;

/**
 * Micronaut Test extension for Pytest.
 */
public class PytestMicronautExtension extends AbstractMicronautExtension<Value> {

    public static final String ID = "_micronaut_test_extension";

    public PytestMicronautExtension(Map<String, Object> pytestProperties, Value node) {
        if (pytestProperties != null) {
            this.testProperties.putAll(pytestProperties);
        }
        node.putMember(ID, this);
    }

    @Override
    protected void resolveTestProperties(Value context, MicronautTestValue testAnnotationValue, Map<String, Object> testProperties) {
        // no-op
    }

    @Override
    protected void alignMocks(Value context, Object instance) {
        // TODO
    }

    @Override
    public void beforeClass(Value context, Class<?> testClass, @Nullable MicronautTestValue testAnnotationValue) {
        super.beforeClass(context, testClass, testAnnotationValue);
    }

    @Override
    public void afterClass(Value context) {
        super.afterClass(context);
    }

    @Override
    public void beforeEach(Value context, @Nullable Object testInstance, @Nullable AnnotatedElement method, List<Property> propertyAnnotations) {
        super.beforeEach(context, testInstance, method, propertyAnnotations);
    }

    @Override
    public void afterEach(Value context) throws Exception {
        super.afterEach(context);
    }

    public ApplicationContext getContext() {
        return this.applicationContext;
    }
}

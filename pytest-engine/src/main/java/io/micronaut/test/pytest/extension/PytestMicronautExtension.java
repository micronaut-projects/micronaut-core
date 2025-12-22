/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

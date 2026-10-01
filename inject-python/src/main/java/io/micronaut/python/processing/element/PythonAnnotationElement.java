/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.processing.element;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.model.ClassDef;
import io.micronaut.python.processing.model.DecoratorDef;

/**
 * Class element implementation for Python annotations, which are declared as decorators.
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class PythonAnnotationElement extends PythonClassElement implements AnnotationElement {

    /**
     * @param classDef    The class definition synthesized for the decorator
     * @param environment The environment
     */
    public PythonAnnotationElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        super(classDef, environment, 0, null, true);
    }

    private PythonAnnotationElement(ClassDef classDef, PythonProcessingEnvironment environment, boolean initializeClassMetadata) {
        super(classDef, environment, 0, null, initializeClassMetadata);
    }

    @Override
    protected PythonClassElement copyThis() {
        return new PythonAnnotationElement(getNativeType(), environment, false);
    }

    @Override
    public boolean isInherited() {
        // the decorators of the synthesized class definition are the decorators applied to the
        // decorator that declares the annotation
        for (DecoratorDef decorator : getNativeType().decorators()) {
            if (AnnotationUtil.ANN_INHERITED.equals(decorator.annotationName())) {
                return true;
            }
        }
        return false;
    }
}

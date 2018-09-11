/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.inject.visitor.ClassElement;
import io.micronaut.inject.visitor.Element;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

/**
 * Represents the void type.
 *
 * @author graemerocher
 * @since 1.0
 */
final class JavaVoidElement implements ClassElement, AnnotationMetadataDelegate {

    @Override
    public boolean isAssignable(String type) {
        return "void".equals(type);
    }

    @Override
    public String getName() {
        return "void";
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public Object getNativeType() {
        return void.class;
    }

    @Override
    public List<Element> getElements(VisitorContext visitorContext) {
        return Collections.emptyList();
    }
}

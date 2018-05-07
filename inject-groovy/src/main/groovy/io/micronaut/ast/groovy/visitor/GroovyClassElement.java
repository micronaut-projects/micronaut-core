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

package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.visitor.ClassElement;
import org.codehaus.groovy.ast.ClassNode;

import java.lang.reflect.Modifier;

/**
 * A class element returning data from a {@link ClassNode}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class GroovyClassElement extends AbstractGroovyElement implements ClassElement {

    private final ClassNode classNode;

    /**
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyClassElement(ClassNode classNode, AnnotationMetadata annotationMetadata) {
        super(annotationMetadata);
        this.classNode = classNode;
    }

    @Override
    public String getName() {
        return classNode.getName();
    }

    @Override
    public boolean isAbstract() {
        return classNode.isAbstract();
    }

    @Override
    public boolean isStatic() {
        return classNode.isStaticClass();
    }

    @Override
    public boolean isPublic() {
        return classNode.isSyntheticPublic() || Modifier.isPublic(classNode.getModifiers());
    }

    @Override
    public boolean isPrivate() {
        return Modifier.isPrivate(classNode.getModifiers());
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(classNode.getModifiers());
    }

    @Override
    public boolean isProtected() {
        return Modifier.isProtected(classNode.getModifiers());
    }

    @Override
    public Object getNativeType() {
        return classNode;
    }
}

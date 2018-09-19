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

import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.ast.groovy.utils.AstClassUtils;
import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.ast.groovy.utils.PublicMethodVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.visitor.ClassElement;
import io.micronaut.inject.visitor.Element;
import io.micronaut.inject.visitor.VisitorContext;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;

import java.lang.reflect.Modifier;
import java.util.*;

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
    public Map<String, ClassElement> getTypeArguments() {
        Map<String, ClassNode> spec = AstGenericUtils.createGenericsSpec(classNode);
        if (!spec.isEmpty()) {
            Map<String, ClassElement> map = new LinkedHashMap<>(spec.size());
            for (Map.Entry<String, ClassNode> entry : spec.entrySet()) {
                map.put(entry.getKey(), new GroovyClassElement(entry.getValue(), getAnnotationMetadata()));
            }
            return Collections.unmodifiableMap(map);
        }
        return Collections.emptyMap();
    }

    @Override
    public boolean isArray() {
        return classNode.isArray();
    }

    @Override
    public String toString() {
        return classNode.getName();
    }

    @Override
    public String getName() {
        if (isArray()) {
            return classNode.getComponentType().getName();
        } else {
            return classNode.getName();
        }
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

    @Override
    public boolean isAssignable(String type) {
        return AstClassUtils.isSubclassOf(classNode, type);
    }

    @Override
    public List<Element> getElements(VisitorContext visitorContext) {
        List<Element> elements = new ArrayList<>();
        new PublicMethodVisitor(((GroovyVisitorContext) visitorContext).getSourceUnit()) {

            private final Set<String> processed = new HashSet<>();

            protected boolean isAcceptable(MethodNode node) {
                return true;
            }

            public void visitField(FieldNode node) {
                super.visitField(node);
                String key = node.getText();
                if (!processed.contains(key)) {
                    processed.add(key);
                    elements.add(new GroovyFieldElement(node, AstAnnotationUtils.getAnnotationMetadata(node)));
                }
            }

            @Override
            public void accept(ClassNode classNode, MethodNode methodNode) {
                elements.add(new GroovyMethodElement(methodNode, AstAnnotationUtils.getAnnotationMetadata(methodNode)));
            }
        }.accept(classNode);

        return elements;
    }
}

/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.ast.groovy.utils

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Internal
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit

import java.lang.annotation.Annotation

/**
 * Utility methods for dealing with annotations within the context of AST
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AstAnnotationUtils {

    private static final Map<AnnotatedNode, AnnotationMetadata> annotationMetadataCache = new ConcurrentLinkedHashMap.Builder<AnnotatedNode, AnnotationMetadata>().maximumWeightedCapacity(100).build()

    /**
     * Get the {@link AnnotationMetadata} for the given annotated node
     *
     * @param annotatedNode The node
     * @return The metadata
     */
    static AnnotationMetadata getAnnotationMetadata(SourceUnit sourceUnit, CompilationUnit compilationUnit, AnnotatedNode annotatedNode) {
        return annotationMetadataCache.computeIfAbsent(annotatedNode, { AnnotatedNode annotatedNode1 ->
            new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit).build(annotatedNode1)
        })
    }

    /**
     * Get the {@link AnnotationMetadata} for the given annotated node
     *
     * @param sourceUnit the source unit
     * @param parent the parent
     * @param annotatedNode The node
     * @return The metadata
     */
    static AnnotationMetadata getAnnotationMetadata(SourceUnit sourceUnit, CompilationUnit compilationUnit, AnnotatedNode parent, AnnotatedNode annotatedNode) {
        new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit).buildForParent(parent, annotatedNode)
    }


    /**
     * Get the {@link AnnotationMetadata} for the given annotated node
     *
     * @param sourceUnit the source unit
     * @param parent the parent
     * @param annotatedNode The node
     * @return The metadata
     */
    static AnnotationMetadata getAnnotationMetadata(SourceUnit sourceUnit, CompilationUnit compilationUnit, AnnotatedNode parent, AnnotatedNode annotatedNode, boolean inheritTypeAnnotations) {
        newBuilder(sourceUnit, compilationUnit).buildForParent(parent, annotatedNode, inheritTypeAnnotations)
    }

    /**
     * Creates a new annotation builder for the given source unit
     * @param sourceUnit The unit
     * @return the builder
     */
    static GroovyAnnotationMetadataBuilder newBuilder(SourceUnit sourceUnit, CompilationUnit compilationUnit) {
        new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit)
    }

    /**
     * Invalidates any cached metadata
     */
    @Internal
    static void invalidateCache() {
        annotationMetadataCache.clear()
    }

    /**
     * Invalidates any cached metadata
     */
    @Internal
    static void invalidateCache(AnnotatedNode node) {
        if (node)
            annotationMetadataCache.remove(node)
    }

    /**
     * Return whether the given annotated node has the given stereotype
     *
     * @param annotatedNode The annotated node
     * @param stereotype The stereotype
     * @return True if it does
     */
    static boolean hasStereotype(SourceUnit sourceUnit, CompilationUnit compilationUnit, AnnotatedNode annotatedNode, String stereotype) {
        return getAnnotationMetadata(sourceUnit, compilationUnit, annotatedNode).hasStereotype(stereotype)
    }
    /**
     * Return whether the given annotated node has the given stereotype
     *
     * @param annotatedNode The annotated node
     * @param stereotype The stereotype
     * @return True if it does
     */
    static boolean hasStereotype(SourceUnit sourceUnit, CompilationUnit compilationUnit, AnnotatedNode annotatedNode, Class<? extends Annotation> stereotype) {
        return hasStereotype(sourceUnit, compilationUnit, annotatedNode, stereotype.getName())
    }

    /**
     * Return whether the given element is annotated with any of the given annotation stereotypes.
     *
     * @param element     The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    static boolean hasStereotype(SourceUnit sourceUnit, CompilationUnit compilationUnit, AnnotatedNode annotatedNode, List<String> stereotypes) {
        if (annotatedNode == null) {
            return false
        }
        AnnotationMetadata annotationMetadata = getAnnotationMetadata(sourceUnit, compilationUnit, annotatedNode)
        for (String stereotype : stereotypes) {
            if (annotationMetadata.hasStereotype(stereotype)) {
                return true
            }
        }
        return false
    }

    /**
     * Whether the node is annotated with any non internal annotations
     *
     * @param declaringType The declaring type
     * @param annotatedNode The annotated node
     * @return True if it is
     */
    static boolean isAnnotated(String declaringType, AnnotatedNode annotatedNode) {
        if (AbstractAnnotationMetadataBuilder.isMetadataMutated(declaringType, annotatedNode)) {
            return true
        }
        for (ann in annotatedNode.annotations) {
            if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(ann.classNode.name)) {
                return true
            }
        }
        return false
    }
    /**
     * Finds an annotation for the given annotated node and type
     *
     * @param annotatedNode The annotated node
     * @param annotationName The annotation type
     * @return The annotation or null
     */
    static AnnotationNode findAnnotation(AnnotatedNode annotatedNode, Class<?> type) {
        String annotationName = type.name
        return findAnnotation(annotatedNode, annotationName)
    }

    /**
     * Finds an annotation for the given annotated node and name
     *
     * @param annotatedNode The annotated node
     * @param annotationName The annotation name
     * @return The annotation or null
     */
    static AnnotationNode findAnnotation(AnnotatedNode annotatedNode, String annotationName) {
        if (annotatedNode != null) {
            List<AnnotationNode> annotations = annotatedNode.getAnnotations()
            for (ann in annotations) {
                ClassNode annotationClassNode = ann.classNode
                if (annotationClassNode.name == annotationName) {
                    return ann
                } else if (!(annotationClassNode.name in AnnotationUtil.INTERNAL_ANNOTATION_NAMES)) {

                    ann = findAnnotation(annotationClassNode, annotationName)
                    if (ann != null) {
                        return ann
                    }
                }
            }
        }
        return null
    }

    /**
     * Returns true if MethodNode is marked with annotationClass
     *
     * @param methodNode A MethodNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    static boolean hasAnnotation(final MethodNode methodNode, final Class<? extends Annotation> annotationClass) {
        def classNode = new ClassNode(annotationClass)
        return hasAnnotation(methodNode, classNode)
    }

    /**
     * Returns true if MethodNode is marked with annotationClassNode
     *
     * @param methodNode A MethodNode to inspect
     * @param annotationClassNode An annotation class to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    static boolean hasAnnotation(MethodNode methodNode, ClassNode annotationClassNode) {
        return !methodNode.getAnnotations(annotationClassNode).isEmpty()
    }
    /**
     * Copy the annotations from one annotated node to another
     *
     * @param from The source annotated node
     * @param to The target annotated node
     */
    static void copyAnnotations(final AnnotatedNode from, final AnnotatedNode to) {
        copyAnnotations(from, to, null, null)
    }

    /**
     * Copy the annotations from one annotated node to another
     *
     * @param from The source annotated node
     * @param to The target annotated node
     * @param included The includes annotations
     * @param excluded The excluded annotations
     */
    static void copyAnnotations(
        final AnnotatedNode from, final AnnotatedNode to, final Set<String> included, final Set<String> excluded) {
        final List<AnnotationNode> annotationsToCopy = from.getAnnotations()
        for (final AnnotationNode node : annotationsToCopy) {
            String annotationClassName = node.getClassNode().getName()
            if ((excluded == null || !excluded.contains(annotationClassName)) &&
                (included == null || included.contains(annotationClassName))) {
                final AnnotationNode copyOfAnnotationNode = cloneAnnotation(node)
                to.addAnnotation(copyOfAnnotationNode)
            }
        }
    }

    /**
     * Clones the given annotation node returning a new one
     *
     * @param node The annotation node
     * @return The cloned annotation node
     */
    static AnnotationNode cloneAnnotation(final AnnotationNode node) {
        final AnnotationNode copyOfAnnotationNode = new AnnotationNode(node.getClassNode())
        final Map<String, Expression> members = node.getMembers()
        for (final Map.Entry<String, Expression> entry : members.entrySet()) {
            copyOfAnnotationNode.addMember(entry.getKey(), entry.getValue())
        }
        return copyOfAnnotationNode
    }
}

/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.visitor;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.Collections;
import java.util.Set;

/**
 * Provides a hook into the compilation process to allow user defined functionality to be created at compile time.
 *
 * @param <C> The annotation required on the class. Use {@link Object} for all classes.
 * @param <E> The annotation required on the element. Use {@link Object} for all elements.
 * @author James Kleeh
 * @since 1.0
 */
public interface TypeElementVisitor<C, E> extends Ordered, Toggleable {

    /**
     * The query which allows to reduce the scope which the visitor is visiting.
     *
     * @return The query if the visitor.
     * @since 4.9
     */
    @Experimental
    default TypeElementQuery query() {
        return TypeElementQuery.DEFAULT;
    }

    /**
     * Executed when a class is encountered that matches the {@literal <}C{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitClass(ClassElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when a method is encountered that matches the {@literal <}E{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitMethod(MethodElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when a constructor is encountered that matches the {@literal <}C{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitConstructor(ConstructorElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when a field is encountered that matches the {@literal <}E{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitField(FieldElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when an enum constant is encountered that matches the {@literal <}E{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     *
     * @since 3.6.0
     */
    default void visitEnumConstant(EnumConstantElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Called once when visitor processing starts, before any class is visited.
     *
     * @param visitorContext The visitor context
     */
    default void start(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * Called at the end of each processing round, after every visitor has visited every class of the round.
     *
     * <p>A round is the set of classes a compiler hands to the visitors together. The classes of the compilation
     * form the first round, and the classes generated as source during a round form the next one. When this method
     * is called, no class of the round is visited anymore, and:</p>
     *
     * <ul>
     *     <li>a bean added with {@link VisitorContext#registerBean(ClassElement, io.micronaut.inject.ast.Element...)}
     *     or {@link ClassElement#addAssociatedBean(ClassElement)} is written with the beans of the round;</li>
     *     <li>a source written with {@link VisitorContext#visitGeneratedSourceFile(String, String, io.micronaut.inject.ast.Element...)}
     *     is compiled, and its classes are visited in a later round, followed by another call of this method.</li>
     * </ul>
     *
     * <p>The rounds of each compiler:</p>
     *
     * <ul>
     *     <li>javac: each annotation processing round, except the final one, in which nothing generated is
     *     processed anymore. {@link #finish(VisitorContext)} is called after this method in every round, the final one
     *     included.</li>
     *     <li>KSP: each call of {@code SymbolProcessor.process}. {@link #finish(VisitorContext)} is called once, after the
     *     last round.</li>
     *     <li>Groovy: the classes of the compilation are a round, visited during canonicalization, and so is each set of
     *     classes generated as source afterwards. {@link #finish(VisitorContext)} is called once, at class generation.</li>
     *     <li>Python: the compilation is a single round.</li>
     * </ul>
     *
     * <p>A round may visit no class matching this visitor, and the elements of a round are only guaranteed to be
     * usable during that round: KSP invalidates them when the round ends.</p>
     *
     * @param visitorContext The visitor context
     * @since 5.3.0
     */
    @Experimental
    default void finishRound(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * Called once, after the last round of the compilation, when no class is visited anymore, before
     * {@link #finish(VisitorContext)}.
     *
     * <p>This is the point for work that has to see every class of the compilation, including the classes that
     * another visitor or annotation processor generated as source in a later round: aggregating what every round
     * visited, adding the beans that follow from it, and validating the result. A bean added here with
     * {@link VisitorContext#registerBean(ClassElement, io.micronaut.inject.ast.Element...)} is written by every
     * compiler, javac, KSP, Groovy and Python. A source generated here is not visited anymore.</p>
     *
     * <p>A visitor that implements this method has to return {@code true} from {@link #aggregatesCompilation()}, so
     * that the build knows before the compilation starts that the visitor has to see every class again whenever a
     * class is changed, added or removed: an incremental compilation otherwise compiles the changed sources only, so
     * the visitors would see only them, here as in every other method, and a visitor that writes nothing for the
     * classes it saw would not be given the others in the next compilation. Everything written as a result of this
     * method, the beans added here, their service entries, and the classes, sources and resources written here,
     * follows from the whole compilation. A bean added here still has to be given the classes it follows from as
     * originating elements:</p>
     *
     * <ul>
     *     <li>javac: the annotation processor that runs a visitor aggregating the compilation is an aggregating
     *     processor for the incremental compilation of Gradle, registered for every class: Gradle processes again
     *     every class of the previous compilation, without compiling them again, whenever a source is changed, added
     *     or removed, and writes again everything the processor wrote. The files are written with every originating
     *     element, except to the filer of Gradle, which gets none: for a visitor that does not declare
     *     {@link #aggregatesCompilation()}, whose processor stays isolating, such a file disables incremental
     *     annotation processing once it is written, which does not help a compilation that writes nothing.</li>
     *     <li>KSP: the files are aggregating outputs of the files of their originating elements, which KSP writes
     *     again, processing those files again, whenever a source of the compilation is changed, added or
     *     removed. KSP has no way to declare a processor aggregating before it runs, so
     *     {@link #aggregatesCompilation()} does not change it: until a visitor wrote something here, an incremental
     *     KSP compilation gives it the changed sources only. KSP also fixes the dependencies of a file when the
     *     {@link io.micronaut.inject.writer.GeneratedFile} is obtained, so a file written here has to be obtained
     *     here, not kept from a round.</li>
     *     <li>Groovy and Python: the originating elements are not reported to the build. Neither compiler tracks the
     *     origins of what the visitors write, and an incremental Python compilation visits the changed Python classes
     *     only.</li>
     * </ul>
     *
     * <p>Where each compiler calls it:</p>
     *
     * <ul>
     *     <li>javac: in the final annotation processing round, in which nothing generated is processed anymore.
     *     The bean definitions are written in that round, without javac warning that they are not processed. A
     *     source generated there is compiled, but javac warns that it is not processed.</li>
     *     <li>KSP: from {@code SymbolProcessor.finish}. KSP invalidates the elements of a round when the round ends,
     *     so an element kept from a {@code visit} method or from {@link #finishRound(VisitorContext)} must not be used
     *     here: look the classes up again by name, with {@link VisitorContext#getClassElement(String)}, which resolves
     *     them anew.</li>
     *     <li>Groovy: at class generation, after the last round.</li>
     *     <li>Python: once for the Python classes, after their single round, on the visitors that visited them.
     *     javac then compiles the Java sources of the compilation and the Java classes generated for the Python
     *     classes, with other instances of the visitors, and calls it once more on those, as for any javac
     *     compilation. A visitor of every class also visits the Java classes generated for the Python classes there,
     *     unless it skips them with {@link #skipClassesGeneratedForOtherLanguages()}, which a visitor that aggregates
     *     its classes here should do, so that it does not contribute a Python class twice.</li>
     * </ul>
     *
     * @param visitorContext The visitor context
     * @since 5.3.0
     */
    @Experimental
    default void finishCompilation(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * Whether the visitor aggregates the classes of the whole compilation in
     * {@link #finishCompilation(VisitorContext)}, which a visitor implementing that method has to declare.
     *
     * <p>It is read before any class is visited, independently of {@link #getVisitorKind()}: the visitor stays
     * isolating for what it writes for each class it visits, and can still add beans. Under javac, the annotation
     * processor that runs such a visitor is an aggregating processor of every class for Gradle's incremental
     * compilation, so that Gradle processes every class again whenever a source is changed, added or removed, even if
     * the previous compilation wrote nothing. That makes every file the processor writes an aggregating output, and
     * an annotation of source retention in the compilation makes Gradle compile every source. KSP, Groovy and Python
     * do not read it: KSP cannot declare a processor aggregating before it runs, and Gradle has no incremental
     * annotation processing for Groovy or Python.</p>
     *
     * @return Whether the visitor aggregates the classes of the compilation in
     * {@link #finishCompilation(VisitorContext)}
     * @since 5.3.0
     */
    @Experimental
    default boolean aggregatesCompilation() {
        return false;
    }

    /**
     * Whether javac should skip the Java representations of classes already visited by another language compiler.
     *
     * <p>It currently covers Python: the Python compiler visits the Python classes with every visitor, then generates
     * a Java class for each of them, which javac compiles in the same compilation. A visitor selected by an annotation
     * never visits those Java classes, but a visitor of every class visits them again, for what the Python compiler
     * added to them, unless it returns {@code true} here, for example because it contributes something for every
     * class it visits and would otherwise contribute a Python class twice. The Java classes extending a Python class
     * are not generated for it, and are visited in any case.</p>
     *
     * @return Whether javac skips the Java classes generated for the classes of another language
     * @since 5.3.0
     */
    @Experimental
    default boolean skipClassesGeneratedForOtherLanguages() {
        return false;
    }

    /**
     * Called when visitor processing finishes.
     *
     * <p>Under KSP and Groovy, this method is called once, when nothing generated as source is compiled anymore. Under
     * javac, it is called at the end of every annotation processing round, after {@link #finishRound(VisitorContext)},
     * the final round included. Prefer {@link #finishRound(VisitorContext)} for work that has to be done once the
     * classes of a round are visited, and {@link #finishCompilation(VisitorContext)} for work that has to be done once
     * every round is over: under KSP the elements of the rounds are invalid by the time this method is called, and
     * no bean can be registered from it.</p>
     *
     * @param visitorContext The visitor context
     */
    default void finish(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * @return The supported default annotation names.
     */
    default Set<String> getSupportedAnnotationNames() {
        Class<?>[] classes = GenericTypeUtils.resolveInterfaceTypeArguments(getClass(), TypeElementVisitor.class);

        if (classes.length == 2) {
            Class<?> classType = classes[0];
            String classTypeName = classType.getName();
            if (classType == Object.class) {
                classTypeName = getClassType();
            }
            if (classTypeName.equals(Object.class.getName())) {
                return Collections.singleton("*");
            } else {
                Class<?> elementType = classes[1];
                String elementTypeName = elementType.getName();
                if (elementTypeName.equals(Object.class.getName())) {
                    elementTypeName = getElementType();
                }
                if (elementTypeName.equals(Object.class.getName())) {
                    return CollectionUtils.setOf(classTypeName);
                } else {
                    return CollectionUtils.setOf(classTypeName, elementTypeName);
                }
            }
        }
        return Collections.singleton("*");
    }

    default String getClassType() {
        return Object.class.getName();
    }

    default String getElementType() {
        return Object.class.getName();
    }

    /**
     * Called once when processor loads.
     *
     * Used to expose visitors custom processor options.
     *
     * @return Set with custom options
     */
    @Experimental
    default Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    /**
     * @return The visitor kind.
     */
    default VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING;
    }

    /**
     * Implementors of the {@link TypeElementVisitor} interface should specify what kind of visitor it is.
     *
     * If the visitor looks at multiple {@link io.micronaut.inject.ast.Element} and builds a file that references
     * multiple {@link io.micronaut.inject.ast.Element} (meaning it doesn't have an originating element) then
     * {@link VisitorKind#AGGREGATING} should be used
     *
     * If the visitor generates classes from an originating {@link io.micronaut.inject.ast.Element} then {@link VisitorKind#ISOLATING} should be used.
     */
    enum VisitorKind {
        /**
         * A visitor that generates a file for each visited element and calls.
         */
        ISOLATING,
        /**
         * A visitor that generates a one or more files in the {@link #finish(VisitorContext)} method computed from visiting multiple {@link io.micronaut.inject.ast.Element} instances.
         */
        AGGREGATING
    }
}

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
package io.micronaut.python.processing.element;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.python.processing.model.ArgumentDef;
import io.micronaut.python.processing.model.ArgumentsDef;
import io.micronaut.python.processing.model.AttributeDef;
import io.micronaut.python.processing.model.ClassDef;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.model.ElementDef;
import io.micronaut.python.processing.model.FunctionDef;
import io.micronaut.python.processing.model.PropertyDef;
import io.micronaut.python.processing.model.TypeRef;
import io.micronaut.python.processing.model.TypeVar;
import io.micronaut.python.processing.util.AnnotationNames;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.ast.TypeVariableBinder;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.inject.writer.MethodGenUtils;
import io.micronaut.python.processing.PythonProcessingEnvironment;

/**
 * Class element implementation for Python classes.
 */
@Experimental
public sealed class PythonClassElement extends AbstractPythonClassElement permits PythonAnnotationElement {
    private static final String MEMBER_KEYS_PROPERTY = "memberKeys";
    private static final String JUNIT_NESTED = "org.junit.jupiter.api.Nested";
    private static final String CONTEXT_POOLED = "io.micronaut.context.python.scope.ContextPooled";
    private static final String INTRODUCTION_INTERFACE_MARKER = "java.io.Serializable";
    private static final String DATACLASS_DECORATOR = "dataclass";

    private Map<String, ClassElement> resolvedTypeArguments;
    private FunctionDef constructor;
    private List<PythonClassElement> inheritedPythonClasses;
    private final List<ClassElement> introductionInterfaces = new ArrayList<>();

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        this(classDef, environment, 0, null, true);
    }

    public PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        this(classDef, environment, arrayDimensions, null, true);
    }

    PythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions, Map<String, ClassElement> resolvedTypeArguments) {
        this(classDef, environment, arrayDimensions, resolvedTypeArguments, true);
    }

    PythonClassElement(ClassDef classDef,
                       PythonProcessingEnvironment environment,
                       int arrayDimensions,
                       Map<String, ClassElement> resolvedTypeArguments,
                       boolean initializeClassMetadata) {
        super(classDef, environment, arrayDimensions);
        this.resolvedTypeArguments = resolvedTypeArguments;
        if (initializeClassMetadata) {
            initializeClassMetadata();
        }
    }

    /**
     * Creates the element of a Python class for the class registry of the processing environment without
     * deriving its class level metadata yet: {@link #initializeClassMetadata()} runs annotation mappers, which
     * may look other Python classes up through the visitor context, so it is applied once every class of the
     * environment is registered.
     *
     * @param classDef    The class definition
     * @param environment The processing environment
     * @return The element
     */
    @Internal
    public static PythonClassElement registered(ClassDef classDef, PythonProcessingEnvironment environment) {
        return new PythonClassElement(classDef, environment, 0, null, false);
    }

    /**
     * Derives the class level metadata that depends on the annotations of the class definition: the introspection
     * excludes, the bean stereotype of a class with property injection points and the interfaces introduced by an
     * introduction advice. Building that metadata runs the annotation mappers of the class annotations.
     */
    @Internal
    public void initializeClassMetadata() {
        excludeIntrospectedProperties(MEMBER_KEYS_PROPERTY);
        markPropertyInjectionBeanCandidate();
        moveIntroductionInterfacesToImplementedInterfaces();
    }

    @Override
    public @org.jspecify.annotations.NonNull ClassElement getType() {
        if (typeAnnotationsKey == null) {
            return this;
        }
        PythonClassElement pythonClassElement = (PythonClassElement) makeCopy();
        pythonClassElement.typeAnnotationsKey = null;
        return pythonClassElement;
    }

    @Override
    protected PythonClassElement copyThis() {
        // makeCopy copies the original metadata and resolved introduction interfaces. Repeating
        // discovery here is costly and cannot produce additional information for the same class.
        return new PythonClassElement(getNativeType(), environment, arrayDimensions, null, false);
    }

    @Override
    protected void copyValues(AbstractPythonElement element) {
        super.copyValues(element);
        if (element instanceof PythonClassElement pythonClassElement) {
            pythonClassElement.resolvedTypeArguments = resolvedTypeArguments;
            pythonClassElement.introductionInterfaces.clear();
            pythonClassElement.introductionInterfaces.addAll(introductionInterfaces);
        }
    }

    public final boolean isPythonSource() {
        return environment.classes().containsKey(getName());
    }

    private void markPropertyInjectionBeanCandidate() {
        if (BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(getAnnotationMetadata())) {
            return;
        }
        // isAbstract() resolves the bases of an introduction type with placeholder bodies through the class
        // elements, which are still being built here: the declaration alone decides (an interceptor carrying
        // the introduction stereotype is a declared bean and returned above)
        if (hasAbstractDeclaration() || (hasPlaceholderBodies() && hasStereotype(Introduction.class))) {
            return;
        }
        if (hasPropertyInjectionPoint()) {
            annotate(Bean.class);
        }
    }

    private boolean hasPropertyInjectionPoint() {
        for (PropertyDef propertyDef : getNativeType().properties()) {
            if (hasPropertyInjectionPoint(propertyDef)) {
                return true;
            }
        }
        for (AttributeDef attributeDef : getNativeType().attributes()) {
            if (hasPropertyInjectionPoint(attributeDef)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPropertyInjectionPoint(PropertyDef propertyDef) {
        return hasPropertyInjectionPoint((ElementDef) propertyDef);
    }

    private boolean hasPropertyInjectionPoint(AttributeDef attributeDef) {
        return hasPropertyInjectionPoint((ElementDef) attributeDef);
    }

    private boolean hasPropertyInjectionPoint(ElementDef element) {
        AnnotationMetadata annotationMetadata = environment.annotationMetadataBuilder().buildDeclared(element);
        return annotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || annotationMetadata.hasStereotype(Property.class)
            || annotationMetadata.hasStereotype(io.micronaut.context.annotation.Value.class);
    }

    @Override
    public BeanElementBuilder addAssociatedBean(ClassElement type) {
        JavaVisitorContext javaVisitorContext = environment.javaVisitorContext();
        if (javaVisitorContext != null) {
            return javaVisitorContext.addAssociatedBean(this, type);
        }
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding associated beans without a Java visitor context");
    }

    @Override
    public boolean isInner() {
        return getNativeType().name().indexOf('$') > -1;
    }

    @Override
    public boolean isStatic() {
        // A Python class has no enclosing instance, so a nested class is a static member type of the
        // generated class of its enclosing class; a JUnit @Nested test class is the exception, JUnit
        // requires an inner class and constructs it with the instance of the enclosing test.
        return isInner() && !hasDeclaredAnnotation(JUNIT_NESTED);
    }

    @Override
    public Optional<ClassElement> getEnclosingType() {
        String name = getNativeType().name();
        int innerSeparator = name.lastIndexOf('$');
        if (innerSeparator < 0) {
            return Optional.empty();
        }
        String enclosingName = name.substring(0, innerSeparator);
        String qualifiedEnclosingName = getNativeType().packageName().isEmpty()
            ? enclosingName
            : getPackageName() + "." + enclosingName;
        return Optional.ofNullable(environment.classes().get(qualifiedEnclosingName));
    }

    /**
     * Whether the Java class generated for this class is a member type of the Java class generated for
     * the enclosing Python class.
     * <p>
     * A class nested in a Python class compiles to a member type of the generated class of its enclosing
     * class (binary name {@code Outer$Inner}, as before), so that Java sees the nesting: JUnit runs a
     * {@code @Nested} test class with the context of the enclosing {@code @MicronautTest}. Enums,
     * interfaces and pooled classes are generated as top-level types named {@code Outer$Inner}, whether
     * they are nested or enclose other classes.
     *
     * @return {@code true} when the generated class is a member type of the enclosing generated class
     */
    public boolean isMemberOfEnclosingType() {
        return isInner()
            && isMemberCandidate(this)
            && getEnclosingType().filter(enclosing -> enclosing instanceof PythonClassElement && isMemberCandidate(enclosing)).isPresent();
    }

    private static boolean isMemberCandidate(ClassElement element) {
        return !element.isEnum()
            && !element.isInterface()
            && !element.hasStereotype(CONTEXT_POOLED);
    }

    @Override
    public String getCanonicalName() {
        if (isMemberOfEnclosingType()) {
            ClassElement enclosing = getEnclosingType().orElseThrow();
            String name = getNativeType().name();
            return enclosing.getCanonicalName() + "." + name.substring(name.lastIndexOf('$') + 1);
        }
        return getName();
    }

    @Override
    protected ClassElement createWithArrayDimensions(int arrayDimensions) {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions);
    }

    @Override
    public String toString() {
        return "Python Class: " + getNativeType().name();
    }

    @Override
    public Optional<MethodElement> getDefaultConstructor() {
        if (findCreatorFunction().isEmpty()) {
            Optional<MethodElement> defaultConstructor = findConstructor();
            if (defaultConstructor.isEmpty() && !hasDeclaredAnnotation(DATACLASS_DECORATOR)) {
                // python class with no explicit constructor return default
                return Optional.of(implicitConstructor());
            } else if (defaultConstructor.isPresent() && defaultConstructor.get().getParameters().length == 0) {
                // a no-arg __init__, declared or inherited
                return defaultConstructor;
            } else if (defaultConstructor.isPresent() && isCallableWithoutArguments(defaultConstructor.get())
                && !MethodGenUtils.hasAllDefaultsParameters(Arrays.asList(defaultConstructor.get().getParameters()))) {
                // every parameter of __init__ has a default (a dataclass whose fields all have defaults) but not
                // all of them are literals the introspection can pass itself: the generated Java class offers a
                // no-arg constructor that creates the object through the Python constructor, which applies them
                return Optional.of(implicitConstructor());
            }
        }
        return super.getDefaultConstructor();
    }

    /**
     * Whether the given {@code __init__} takes parameters that all have a default value, so the Python
     * class can be instantiated without arguments.
     *
     * @param constructor The constructor
     * @return True if it is a constructor whose parameters all have defaults
     */
    public static boolean isCallableWithoutArguments(MethodElement constructor) {
        if (!(constructor instanceof ConstructorElement) || !(constructor.getNativeType() instanceof FunctionDef functionDef)) {
            return false;
        }
        List<ArgumentDef> arguments = functionDef.arguments() == null ? List.of() : functionDef.arguments().arguments();
        return !arguments.isEmpty() && arguments.stream().allMatch(ArgumentDef::hasDefaultValue);
    }

    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        // First check for @Creator methods (static factory methods)
        Optional<MethodElement> creator = findCreatorFunction();
        if (creator.isPresent()) {
            return creator;
        }

        // Fall back to regular constructor
        Optional<MethodElement> primaryConstructor = findConstructor();
        if (primaryConstructor.isPresent()) {
            return primaryConstructor;
        }
        // A class that neither declares nor inherits __init__ is constructed without arguments. Report that
        // implicit constructor like the Java model reports the implicit default constructor of a Java class, so
        // that visitors resolving the primary constructor (associated beans, module imports) can use it.
        return Optional.of(implicitConstructor());
    }

    private Optional<MethodElement> findCreatorFunction() {
        for (FunctionDef function : getNativeType().functions()) {
            if (function.isStatic()) {
                // Check if this static method has @Creator annotation
                for (DecoratorDef decorator : function.decorators()) {
                    if ("Creator".equals(decorator.name()) ||
                        "io.micronaut.core.annotation.Creator".equals(decorator.annotationName())) {
                        return Optional.of(new PythonMethodElement(function, environment, this, this, environment.metadataFactory()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the {@code __init__} of this class: the declared one (including the constructor derived from the
     * fields of a dataclass), otherwise the one inherited from the first Python base class in the method
     * resolution order that declares one, which Python calls when the subclass is instantiated.
     *
     * @return The constructor, if the class declares or inherits one
     */
    private Optional<MethodElement> findConstructor() {
        FunctionDef declaredConstructor = declaredConstructor();
        if (declaredConstructor != null) {
            return Optional.of(new PythonConstructorElement(declaredConstructor, environment, this, this, environment.metadataFactory()));
        }
        for (PythonClassElement pythonSuperType : inheritedPythonClasses()) {
            FunctionDef inheritedConstructor = pythonSuperType.declaredConstructor();
            if (inheritedConstructor != null) {
                return Optional.of(new PythonConstructorElement(inheritedConstructor, environment, pythonSuperType, this, environment.metadataFactory()));
            }
        }
        return Optional.empty();
    }

    /**
     * The Python classes this class inherits from, in Python's method resolution order (the C3 linearization
     * of the compiled Python bases), without this class: the order in which Python looks up an inherited
     * member such as {@code __init__}. For {@code class C(Mixin, Base)} the mixin comes before the base and
     * both before the bases of the mixin.
     *
     * @return The inherited Python classes in method resolution order
     */
    private List<PythonClassElement> inheritedPythonClasses() {
        if (inheritedPythonClasses == null) {
            List<PythonClassElement> linearization = linearize(new LinkedHashSet<>());
            inheritedPythonClasses = List.copyOf(linearization.subList(1, linearization.size()));
        }
        return inheritedPythonClasses;
    }

    private List<PythonClassElement> linearize(Set<String> inProgress) {
        List<PythonClassElement> linearization = new ArrayList<>();
        linearization.add(this);
        if (!inProgress.add(getName())) {
            // a cyclic hierarchy, which Python rejects: stop here
            return linearization;
        }
        List<PythonClassElement> bases = new ArrayList<>();
        for (TypeRef basis : getNativeType().bases()) {
            if (findPythonClass(basis) instanceof PythonClassElement pythonBase
                && bases.stream().noneMatch(base -> base.getName().equals(pythonBase.getName()))) {
                bases.add(pythonBase);
            }
        }
        List<List<PythonClassElement>> sequences = new ArrayList<>(bases.size() + 1);
        for (PythonClassElement base : bases) {
            sequences.add(new ArrayList<>(base.linearize(inProgress)));
        }
        sequences.add(bases);
        merge(sequences, linearization);
        inProgress.remove(getName());
        return linearization;
    }

    /**
     * The C3 merge: repeatedly takes the head of the first sequence that does not appear in the tail of any
     * other sequence. A hierarchy without such a head is inconsistent (Python refuses to create the class);
     * the first remaining head is taken then so that every base is still visited.
     */
    private static void merge(List<List<PythonClassElement>> sequences, List<PythonClassElement> linearization) {
        while (true) {
            sequences.removeIf(List::isEmpty);
            if (sequences.isEmpty()) {
                return;
            }
            PythonClassElement next = sequences.stream()
                .map(List::getFirst)
                .filter(head -> sequences.stream().noneMatch(sequence -> indexOf(sequence, head) > 0))
                .findFirst()
                .orElseGet(() -> sequences.getFirst().getFirst());
            linearization.add(next);
            for (List<PythonClassElement> sequence : sequences) {
                if (!sequence.isEmpty() && sequence.getFirst().getName().equals(next.getName())) {
                    sequence.removeFirst();
                }
            }
        }
    }

    private static int indexOf(List<PythonClassElement> sequence, PythonClassElement element) {
        for (int i = 0; i < sequence.size(); i++) {
            if (sequence.get(i).getName().equals(element.getName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether this class is decorated with {@code @dataclass}.
     *
     * @return {@code true} for a Python dataclass
     */
    public boolean isDataclass() {
        return hasDataclassDecorator(getNativeType().decorators());
    }

    /**
     * Completes the {@code __init__} the processor derived from the fields of a dataclass with the fields of its
     * dataclass bases. Python collects the fields of every dataclass in the method resolution order, walked from
     * the most distant base to the class itself, plain classes in between contributing nothing: the fields of the
     * bases come first and a field declared again keeps the position of its first declaration. An explicit
     * {@code __init__} is used as declared, as in Python.
     *
     * @param constructor The constructor of the class definition, may be {@code null}
     * @return The constructor to use, may be {@code null}
     */
    private @Nullable FunctionDef withInheritedDataclassFields(@Nullable FunctionDef constructor) {
        if (!isDataclass() || (constructor != null && !hasDataclassDecorator(constructor.decorators()))) {
            return constructor;
        }
        Map<String, ArgumentDef> fields = new LinkedHashMap<>();
        List<PythonClassElement> mro = pythonMro();
        for (int i = mro.size() - 1; i >= 0; i--) {
            PythonClassElement base = mro.get(i);
            if (!base.isDataclass()) {
                continue;
            }
            List<ArgumentDef> inheritedFields = base.getPrimaryConstructor()
                .filter(PythonConstructorElement.class::isInstance)
                .map(superConstructor -> ((PythonConstructorElement) superConstructor).getNativeType().arguments().arguments())
                .orElse(List.of());
            for (ArgumentDef inheritedField : inheritedFields) {
                fields.put(inheritedField.name(), inheritedField);
            }
        }
        if (fields.isEmpty()) {
            return constructor;
        }
        if (constructor != null) {
            for (ArgumentDef field : constructor.arguments().arguments()) {
                fields.put(field.name(), field);
            }
        }
        FunctionDef template = constructor != null ? constructor : new FunctionDef(FunctionDef.CONSTRUCTOR_NAME, dataclassConstructorDecorators());
        return new FunctionDef(
            template.name(),
            ArgumentsDef.of(List.copyOf(fields.values())),
            template.decorators(),
            template.returnType(),
            template.typeComment(),
            template.typeParams(),
            template.documentation(),
            template.isAbstract(),
            template.isStatic(),
            template.isAsync(),
            template.hasReturnValue(),
            template.hasPlaceholderBody(),
            null,
            template.superArguments()
        ).withClassDef(getNativeType());
    }

    /**
     * The Python bases of the class in method resolution order (the C3 linearization Python uses), the class
     * itself excluded. Bases that are not Python classes (Java types) carry no dataclass fields and are left out.
     *
     * @return The linearized Python bases
     */
    private List<PythonClassElement> pythonMro() {
        List<PythonClassElement> bases = new ArrayList<>();
        for (TypeRef base : getNativeType().bases()) {
            if (findPythonClass(base) instanceof PythonClassElement pythonBase && !pythonBase.getName().equals(getName())) {
                bases.add(pythonBase);
            }
        }
        List<List<PythonClassElement>> sequences = new ArrayList<>(bases.size() + 1);
        for (PythonClassElement base : bases) {
            List<PythonClassElement> baseMro = new ArrayList<>();
            baseMro.add(base);
            baseMro.addAll(base.pythonMro());
            sequences.add(baseMro);
        }
        sequences.add(new ArrayList<>(bases));
        return c3Merge(sequences);
    }

    /**
     * Merges the linearizations of the bases: the next class is the first head that appears in no tail. Python
     * rejects a hierarchy without such a head; here the first head is taken so that a constructor is still derived.
     */
    private static List<PythonClassElement> c3Merge(List<List<PythonClassElement>> sequences) {
        List<PythonClassElement> result = new ArrayList<>();
        while (true) {
            sequences.removeIf(List::isEmpty);
            if (sequences.isEmpty()) {
                return result;
            }
            PythonClassElement next = null;
            for (List<PythonClassElement> sequence : sequences) {
                PythonClassElement head = sequence.getFirst();
                if (sequences.stream().noneMatch(other -> indexOfClass(other, head) > 0)) {
                    next = head;
                    break;
                }
            }
            if (next == null) {
                next = sequences.getFirst().getFirst();
            }
            result.add(next);
            for (List<PythonClassElement> sequence : sequences) {
                if (indexOfClass(sequence, next) == 0) {
                    sequence.removeFirst();
                }
            }
        }
    }

    private static int indexOfClass(List<PythonClassElement> sequence, PythonClassElement classElement) {
        for (int i = 0; i < sequence.size(); i++) {
            if (sequence.get(i).getName().equals(classElement.getName())) {
                return i;
            }
        }
        return -1;
    }

    private static List<DecoratorDef> dataclassConstructorDecorators() {
        return List.of(new DecoratorDef(DATACLASS_DECORATOR, DATACLASS_DECORATOR));
    }

    private static boolean hasDataclassDecorator(List<DecoratorDef> decorators) {
        return decorators.stream().anyMatch(decorator -> DATACLASS_DECORATOR.equals(decorator.name()) || "dataclasses.dataclass".equals(decorator.name()));
    }

    /**
     * Whether the class declares or inherits an {@code __init__}, or declares a {@code @Creator} function: a
     * class constructed with arguments is never an interface.
     */
    private boolean hasConstructorOrCreator() {
        return findConstructor().isPresent() || findCreatorFunction().isPresent();
    }

    /**
     * The {@code __init__} this class declares, completed with the fields of its dataclass bases for a dataclass;
     * {@code null} when the class declares none.
     */
    private @Nullable FunctionDef declaredConstructor() {
        if (constructor == null) {
            constructor = withInheritedDataclassFields(getNativeType().constructor());
        }
        return constructor;
    }

    private PythonConstructorElement implicitConstructor() {
        return new PythonConstructorElement(new FunctionDef(FunctionDef.CONSTRUCTOR_NAME), environment, this, this, environment.metadataFactory());
    }

    @Override
    public boolean isAssignable(String type) {
        if (Object.class.getName().equals(type) || getName().equals(type)) {
            return true;
        }
        for (TypeRef base : getNativeType().bases()) {
            if (base.name().equals(type)) {
                return true;
            }
            ClassElement baseElement = findPythonClass(base);
            if (baseElement == null) {
                // a Java class base: assignable to its superclasses and the interfaces they implement
                baseElement = toJavaType(base).orElse(null);
            }
            if (baseElement != null && baseElement.isAssignable(type)) {
                return true;
            }
        }

        for (ClassElement anInterface : getInterfaces()) {
            if (anInterface.isAssignable(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the interface is implemented through an {@code @Introduction} of the class rather than
     * declared as a base of the Python class. The proxy of the class implements its methods.
     *
     * @param anInterface The interface
     * @return Whether it is an introduction interface
     */
    public boolean isIntroductionInterface(ClassElement anInterface) {
        for (ClassElement introductionInterface : introductionInterfaces) {
            if (introductionInterface.getName().equals(anInterface.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        List<TypeRef> bases = getNativeType().bases();
        Set<ClassElement> interfaces = new LinkedHashSet<>(introductionInterfaces);
        for (TypeRef basis : bases) {
            ClassElement baseElement = findPythonClass(basis);
            if (baseElement != null) {
                if (baseElement.isInterface()) {
                    interfaces.add(resolveTypeArguments(baseElement, basis));
                }
            } else {
                ClassElement javaInterface = toJavaType(basis).orElse(null);
                if (javaInterface != null && javaInterface.isInterface()) {
                    interfaces.add(javaInterface);
                }
            }
        }
        return interfaces;
    }

    /**
     * Whether the class compiles to a Java interface. A class is an interface when it has no state
     * (no constructor, attributes or properties) and only declares abstract methods, in one of two shapes:
     * <ul>
     *     <li>a plain abstract class or {@code Protocol} without a bean or interceptor stereotype, the
     *     Python spelling of a Java interface;</li>
     *     <li>an {@link #isIntroductionInterface() introduction interface}: a class decorated with an
     *     {@link Introduction} stereotype ({@code @Client}, an AI service, ...) that has no class base and whose
     *     instance methods are all abstract, none of which declares a {@code *args} parameter. Micronaut
     *     implements such a type with an introduction proxy, and frameworks that build the implementation
     *     reflectively need the Java interface, not a class wrapping a Python object.</li>
     * </ul>
     *
     * @return True if the class compiles to an interface
     */
    @Override
    public boolean isInterface() {
        // Whether a Python class compiles to a Java interface is a property of the class declaration.
        // A copy of the element carrying other annotation metadata (the produced type of a factory
        // method merges the method's annotations, a scope or around advice among them) must answer the
        // same as the element the stub was generated from, or a proxy of the type extends an interface.
        if (presetAnnotationMetadata != null
            && environment.classes().get(getName()) instanceof PythonClassElement declaredElement
            && declaredElement != this
            && declaredElement.presetAnnotationMetadata == null) {
            return declaredElement.isInterface();
        }
        if (hasStereotype(Introspected.class)
            || hasConstructorOrCreator()
            || !getNativeType().attributes().isEmpty()
            || !getNativeType().properties().isEmpty()) {
            return false;
        }
        boolean hasInterfaceBase = hasInterfaceBase();
        boolean isIntroduction = hasIntroductionStereotype();
        if (!isIntroduction
            && ((!hasInterfaceBase && BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(getAnnotationMetadata()))
                || hasStereotype(InterceptorBinding.class))) {
            return false;
        }
        List<MethodElement> declaredMethods = getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared());
        if (declaredMethods.isEmpty()) {
            return hasInterfaceBase;
        }
        if (isIntroduction) {
            // static functions become static interface methods bridged to Python; the instance
            // methods are all implemented by the introduction advice. A method with a `*args` parameter
            // keeps the type a class served by the runtime proxy, which collects the positional arguments
            // Python passes into the trailing array: the generated interface would declare a plain array
            // parameter (not a Java varargs one) that a Python caller could not spread into through host interop.
            // A class base (a Python class that is not an interface, or a Java class) keeps the type a class
            // too: an interface cannot extend it, and the behaviour and state the base brings need the Python
            // object behind the bean.
            return declaredMethods.stream().allMatch(method -> method.isAbstract() || method.isStatic())
                && declaredMethods.stream().anyMatch(MethodElement::isAbstract)
                && declaredMethods.stream().noneMatch(PythonClassElement::isDeclaredBeanMethod)
                && declaredMethods.stream().noneMatch(MethodElement::isVarArgs)
                && getSuperType().isEmpty();
        }
        return declaredMethods.stream().allMatch(MethodElement::isAbstract)
            && declaredMethods.stream().noneMatch(PythonClassElement::isIntroductionFactoryMethod);
    }

    /**
     * Whether this class is an interface implemented by an introduction proxy: it is decorated with an
     * {@link Introduction} stereotype and {@link #isInterface() compiles to a Java interface}.
     *
     * @return True if the class is an introduction interface
     */
    public boolean isIntroductionInterface() {
        return hasIntroductionStereotype() && isInterface();
    }

    private boolean hasIntroductionStereotype() {
        return hasStereotype(Introduction.class) && !isAssignable(Interceptor.class);
    }

    private static boolean isDeclaredBeanMethod(MethodElement method) {
        return method.hasDeclaredAnnotation(Bean.class) || method.hasDeclaredStereotype(Bean.class);
    }

    private boolean hasInterfaceBase() {
        for (TypeRef basis : getNativeType().bases()) {
            if (isProtocolType(basis.name())) {
                return true;
            }
            ClassElement baseElement = findPythonClass(basis);
            if (baseElement != null) {
                if (baseElement.isInterface()) {
                    return true;
                }
            } else {
                ClassElement javaInterface = toJavaType(basis).orElse(null);
                if (javaInterface != null && javaInterface.isInterface()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isProtocolType(String typeName) {
        return typeName.equals("typing.Protocol")
            || typeName.equals("typing_extensions.Protocol")
            || typeName.equals("Protocol");
    }

    private static boolean isIntroductionFactoryMethod(MethodElement method) {
        return method.hasAnnotation("io.micronaut.context.annotation.Mapper")
            || method.hasAnnotation("io.micronaut.context.annotation.Mapper$Mapping")
            // Visitors can add @Bean directly; treat that like @Bean stereotypes
            // when deciding whether abstract methods require introduction wiring.
            || method.hasDeclaredAnnotation(Bean.class)
            || method.hasDeclaredStereotype(Bean.class)
            || method.hasStereotype(Introduction.class);
    }

    private void moveIntroductionInterfacesToImplementedInterfaces() {
        JavaVisitorContext javaVisitorContext = environment.javaVisitorContext();
        if (javaVisitorContext == null) {
            return;
        }
        Set<String> interfaceNames = new LinkedHashSet<>();
        collectIntroductionInterfaceNames(javaVisitorContext, getNativeType().decorators(), interfaceNames);
        if (interfaceNames.isEmpty()) {
            return;
        }
        for (String interfaceName : interfaceNames) {
            javaVisitorContext.getClassElement(interfaceName)
                .filter(ClassElement::isInterface)
                .ifPresent(introductionInterfaces::add);
        }
        if (!introductionInterfaces.isEmpty()) {
            annotate(Introduction.class, builder ->
                builder.member("interfaces", new AnnotationClassValue<?>[]{new AnnotationClassValue<>(INTRODUCTION_INTERFACE_MARKER)})
            );
        }
    }

    private void collectIntroductionInterfaceNames(JavaVisitorContext javaVisitorContext, List<DecoratorDef> decorators, Set<String> interfaceNames) {
        for (DecoratorDef decorator : decorators) {
            if (Introduction.class.getName().equals(decorator.annotationName())) {
                collectIntroductionInterfaceNames(decorator.members().get("interfaces"), interfaceNames);
            }
            javaVisitorContext.getClassElement(decorator.annotationName())
                .map(annotationElement -> annotationElement.getAnnotation(Introduction.class))
                .ifPresent(introduction -> collectIntroductionInterfaceNames(introduction, interfaceNames));
            javaVisitorContext.getClassElement(decorator.annotationName()).filter(annotationElement -> decorator.members().isEmpty()).ifPresent(annotationElement ->
                environment.annotationMetadataBuilder()
                    .mapAnnotation(decorator)
                    .forEach(annotationValue -> collectIntroductionInterfaceNames(javaVisitorContext, annotationValue, interfaceNames))
            );
            collectIntroductionInterfaceNames(javaVisitorContext, decorator.stereotypes(), interfaceNames);
        }
    }

    private void collectIntroductionInterfaceNames(JavaVisitorContext javaVisitorContext, AnnotationValue<?> annotationValue, Set<String> interfaceNames) {
        if (Introduction.class.getName().equals(annotationValue.getAnnotationName())) {
            collectIntroductionInterfaceNames(annotationValue, interfaceNames);
        }
        javaVisitorContext.getClassElement(annotationValue.getAnnotationName())
            .map(annotationElement -> annotationElement.getAnnotation(Introduction.class))
            .ifPresent(introduction -> collectIntroductionInterfaceNames(introduction, interfaceNames));
        List<AnnotationValue<?>> stereotypes = annotationValue.getStereotypes();
        if (stereotypes != null) {
            stereotypes.forEach(stereotype -> collectIntroductionInterfaceNames(javaVisitorContext, stereotype, interfaceNames));
        }
    }

    private void collectIntroductionInterfaceNames(AnnotationValue<?> introduction, Set<String> interfaceNames) {
        for (AnnotationClassValue<?> interfaceValue : introduction.annotationClassValues("interfaces")) {
            interfaceNames.add(AnnotationNames.rawTypeName(interfaceValue.getName()));
        }
    }

    private void collectIntroductionInterfaceNames(Object value, Set<String> interfaceNames) {
        if (value == null) {
            return;
        }
        if (value instanceof AnnotationClassValue<?> annotationClassValue) {
            interfaceNames.add(AnnotationNames.rawTypeName(annotationClassValue.getName()));
        } else if (value instanceof AnnotationClassValue<?>[] annotationClassValues) {
            for (AnnotationClassValue<?> annotationClassValue : annotationClassValues) {
                interfaceNames.add(AnnotationNames.rawTypeName(annotationClassValue.getName()));
            }
        } else if (value instanceof String interfaceName) {
            interfaceNames.add(AnnotationNames.rawTypeName(interfaceName));
        } else if (value instanceof String[] names) {
            for (String name : names) {
                interfaceNames.add(AnnotationNames.rawTypeName(name));
            }
        }
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        List<TypeRef> bases = getNativeType().bases();

        if (!bases.isEmpty()) {
            for (TypeRef base : bases) {
                ClassElement baseElement = findPythonClass(base);
                if (baseElement != null) {
                    if (!baseElement.isInterface()) {
                        return Optional.of(resolveTypeArguments(baseElement, base));
                    }
                } else  {
                    // maybe a java type
                    ClassElement javaSuper = toJavaType(base).orElse(null);
                    if (javaSuper != null && !javaSuper.isInterface()) {
                        return Optional.of(javaSuper);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private ClassElement resolveTypeArguments(ClassElement baseElement, TypeRef base) {
        List<? extends GenericPlaceholderElement> declaredGenericPlaceholders = baseElement.getDeclaredGenericPlaceholders();
        List<TypeRef> typeArguments = base.typeArguments();
        if (!typeArguments.isEmpty() && declaredGenericPlaceholders != null && !declaredGenericPlaceholders.isEmpty() && typeArguments.size() == declaredGenericPlaceholders.size()) {
            // The map is keyed in the order the base declares its variables: a bean introspection and a bean
            // definition report the arguments of a type as a list, in that order
            Map<String, ClassElement> resolvedTypeArguments = new LinkedHashMap<>(declaredGenericPlaceholders.size());
            Map<String, ClassElement> boundGenerics = typeVariableBindings();
            for (int i = 0; i < declaredGenericPlaceholders.size(); i++) {
                GenericPlaceholderElement placeHolder = declaredGenericPlaceholders.get(i);
                TypeRef typeRef = typeArguments.get(i);
                ClassElement resolvedType = environment.visitorContext().getTypeResolver().resolve(typeRef, boundGenerics);
                String variableName = placeHolder.getVariableName();
                resolvedTypeArguments.put(variableName, resolvedType);
            }
            return baseElement.withTypeArguments(resolvedTypeArguments);
        }
        if (typeArguments.isEmpty() && declaredGenericPlaceholders != null && !declaredGenericPlaceholders.isEmpty()) {
            Map<String, ClassElement> resolvedTypeArguments = new LinkedHashMap<>(declaredGenericPlaceholders.size());
            for (GenericPlaceholderElement placeholder : declaredGenericPlaceholders) {
                resolvedTypeArguments.put(placeholder.getVariableName(), GenericBindings.firstBound(placeholder));
            }
            return baseElement.withTypeArguments(resolvedTypeArguments);
        }
        return baseElement;
    }

    private Optional<ClassElement> toJavaType(TypeRef typeRef) {
        ClassElement baseType = environment.visitorContext().getTypeResolver().resolve(typeRef, typeVariableBindings());
        if (baseType != null && !baseType.getName().equals(Object.class.getName())) {
            return Optional.of(baseType);
        }
        return Optional.empty();
    }

    private Map<String, ClassElement> typeVariableBindings() {
        if (resolvedTypeArguments == null) {
            // Bases of an open generic type must retain its variables so recursive supertype
            // arguments can subsequently be bound through each level of the hierarchy.
            return GenericBindings.declared(this, true);
        }
        return new LinkedHashMap<>(resolvedTypeArguments);
    }

    @Override
    public Map<String, ClassElement> getTypeArguments() {
        if (resolvedTypeArguments == null) {
            List<? extends GenericPlaceholderElement> placeholders = getDeclaredGenericPlaceholders();
            if (placeholders.isEmpty()) {
                return super.getTypeArguments();
            }
            Map<String, ClassElement> typeArguments = new LinkedHashMap<>(placeholders.size());
            for (GenericPlaceholderElement placeholder : placeholders) {
                typeArguments.put(placeholder.getVariableName(), GenericBindings.firstBound(placeholder));
            }
            return typeArguments;
        }
        return resolvedTypeArguments;
    }

    @Override
    public Map<String, Map<String, ClassElement>> getAllTypeArguments() {
        // Python can have multiple concrete bases, so the traversal stays on the native bases instead of
        // the super type and interfaces ClassElement's default implementation walks.
        Map<String, Map<String, ClassElement>> result = new LinkedHashMap<>();
        for (TypeRef base : getNativeType().bases()) {
            ClassElement baseElement = findPythonClass(base);
            ClassElement resolvedBase = baseElement == null
                ? toJavaType(base).orElse(null)
                : resolveTypeArguments(baseElement, base);
            if (resolvedBase == null) {
                continue;
            }
            // The arguments of the base are written in this type's variables, while the types above it are read
            // from the base as it declares them, in its own variables, and bound through what this type gives it.
            // Reading them from the resolved base instead would bind them a second time, because resolving a base
            // substitutes this type's arguments all the way up
            ClassElement declaredBase = baseElement == null ? resolvedBase.getRawClassElement() : baseElement;
            Map<String, ClassElement> baseTypeArguments = resolvedBase.getTypeArguments();
            String baseName = resolvedBase.getName();
            declaredBase.getAllTypeArguments().forEach((typeName, typeArguments) -> result.put(
                typeName,
                typeName.equals(baseName) ? baseTypeArguments : TypeVariableBinder.bind(typeArguments, baseTypeArguments)
            ));
        }
        result.put(getName(), getTypeArguments());
        return result;
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
        return new PythonClassElement(getNativeType(), environment, arrayDimensions, typeArguments);
    }

    final boolean hasExplicitTypeArguments() {
        return resolvedTypeArguments != null;
    }

    @NonNull
    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        return getNativeType().typeParams().stream()
            .map(typeVar -> new PythonGenericPlaceholderElement(typeVar, environment, resolveTypeVarBounds(typeVar), this))
            .toList();
    }

    private List<ClassElement> resolveTypeVarBounds(TypeVar typeVar) {
        List<ClassElement> bounds = new ArrayList<>();
        addTypeVarBound(bounds, typeVar.bound());
        for (Object constraint : typeVar.constraints()) {
            addTypeVarBound(bounds, constraint);
        }
        return bounds;
    }

    private void addTypeVarBound(List<ClassElement> bounds, Object bound) {
        if (bound == null) {
            return;
        }
        TypeRef typeRef = bound instanceof TypeRef tr ? tr : new TypeRef(bound.toString());
        ClassElement pythonClass = findPythonClass(typeRef);
        if (pythonClass != null && pythonClass.getName().equals(getName())) {
            return;
        }
        ClassElement boundElement = environment.visitorContext().getTypeResolver().resolve(typeRef, Map.of());
        if (!Object.class.getName().equals(boundElement.getName())) {
            bounds.add(boundElement);
        }
    }

}

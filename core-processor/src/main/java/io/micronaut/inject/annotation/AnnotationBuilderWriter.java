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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AbstractAnnotationBuilder;
import io.micronaut.core.annotation.AnnotationBuilder;
import io.micronaut.core.annotation.AnnotationBuilderRegistry;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.convert.ConversionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.InterfaceDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the {@link AnnotationBuilder} of an annotation type and the implementation of the annotation it creates.
 *
 * <p>The implementation has a field for each member, which its constructor reads from the given values, or the
 * defaults, and converts to the type of the member: with the methods of {@link ConversionUtils} for the basic types,
 * with {@link ConversionUtils} for classes, and with a generated {@code toAnnotation_<Type>} method for each
 * nested annotation type. {@code hashCode} and {@code equals} are generated
 * over the members, as {@link Annotation} defines them; the annotation value is created when first asked for and kept. The builder extends {@link AbstractAnnotationBuilder} and adds the description of the
 * annotation type: the members and the defaults.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class AnnotationBuilderWriter {

    private static final ClassTypeDef CONVERSION_SERVICE_TYPE = ClassTypeDef.of(ConversionService.class);
    private static final ClassTypeDef MAP_TYPE = ClassTypeDef.of(Map.class);
    private static final ClassTypeDef CONVERSION_UTILS = ClassTypeDef.of(ConversionUtils.class);
    private static final ClassTypeDef ANNOTATION_BUILDER_TYPE = ClassTypeDef.of(AnnotationBuilder.class);
    private static final ClassTypeDef ANNOTATION_VALUE_TYPE = ClassTypeDef.of(AnnotationValue.class);
    private static final ClassTypeDef ARRAYS = ClassTypeDef.of(Arrays.class);
    private static final ClassTypeDef OBJECTS = ClassTypeDef.of(Objects.class);
    private static final String ANNOTATION_VALUE_FIELD = "$annotationValue";
    private static final TypeDef OBJECT_ARRAY = TypeDef.OBJECT.array();

    private AnnotationBuilderWriter() {
    }

    /**
     * Writes the builder and the implementation for an annotation type.
     *
     * @param holderName     The binary name of the type or package that lists the annotation, the generated
     *                       classes are placed next to it
     * @param annotationType The annotation type
     * @param origin         The originating element
     * @param context        The visitor context
     * @throws IOException When a class cannot be written
     */
    static void write(String holderName, ClassElement annotationType, Element origin, VisitorContext context) throws IOException {
        String annotationName = annotationType.getName();
        String baseName = holderName + "$" + AnnotationBuilderRegistry.mangle(annotationName);
        String builderName = baseName + AnnotationBuilderRegistry.BUILDER_SUFFIX;
        String implementationName = baseName + "$Impl";

        List<Member> members = membersOf(annotationType);

        // the type of an annotation is an interface, which a type made from a name does not know, and the calls of
        // its accessors have to be interface calls: a type made from the definition of an interface knows it
        ClassTypeDef annotationTypeDef = ClassTypeDef.of(InterfaceDef.builder(annotationName).build());
        write(implementation(implementationName, annotationTypeDef, members), implementationName, origin, context);
        write(builder(builderName, implementationName, annotationType, annotationTypeDef, members, context), builderName, origin, context);
        context.visitServiceDescriptor(AnnotationBuilder.class, builderName, origin);
    }

    /**
     * The members of the annotation type: the abstract methods of a Java annotation, the properties of a Kotlin one.
     */
    private static List<Member> membersOf(ClassElement annotationType) {
        List<Member> members = new ArrayList<>();
        for (MethodElement method : annotationType.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyAbstract())) {
            members.add(new Member(method.getName(), method.getReturnType()));
        }
        if (members.isEmpty()) {
            for (PropertyElement property : annotationType.getBeanProperties(PropertyElementQuery.of(annotationType))) {
                members.add(new Member(property.getName(), property.getType()));
            }
        }
        return members;
    }

    /**
     * A member of an annotation type.
     *
     * @param name The name, which is the name of the accessor
     * @param type The declared type
     */
    private record Member(String name, ClassElement type) {

        /**
         * @return The type of the accessor: a Kotlin class reference is a {@link Class} on the JVM
         */
        TypeDef typeDef() {
            if (isClassReference(type.isArray() ? type.fromArray() : type)) {
                return type.isArray() ? TypeDef.CLASS.array() : TypeDef.CLASS;
            }
            return TypeDef.erasure(type);
        }

        /**
         * @return The element type of an array member, the type otherwise
         */
        ClassElement component() {
            return type.isArray() ? type.fromArray() : type;
        }

        boolean isArray() {
            return type.isArray();
        }

        /**
         * A member that is neither a basic type, a class, an enum nor an array of those is an annotation: those
         * are the types an annotation member can have.
         */
        boolean isAnnotation() {
            TypeDef def = typeDef();
            ClassElement component = component();
            return !(def.isPrimitive() || (def instanceof TypeDef.Array array && array.componentType().isPrimitive())
                || isClassReference(component) || component.isEnum() || String.class.getName().equals(component.getName()));
        }

        boolean isClass() {
            return isClassReference(component());
        }

        boolean isPrimitive() {
            return !isArray() && typeDef().isPrimitive();
        }

        private static boolean isClassReference(ClassElement element) {
            return Class.class.getName().equals(element.getName()) || "kotlin.reflect.KClass".equals(element.getName());
        }
    }

    private static void write(ClassDef classDef, String name, Element origin, VisitorContext context) throws IOException {
        try (OutputStream outputStream = context.visitClass(name, origin)) {
            outputStream.write(ByteCodeWriterUtils.writeByteCode(classDef, context));
        }
    }

    private static ClassDef implementation(String name, ClassTypeDef annotationTypeDef, List<Member> members) {
        ClassTypeDef implementationType = ClassTypeDef.of(name);
        // a method for each nested annotation type, which reads a nested annotation of the type
        Map<String, MethodDef> annotationMethods = new LinkedHashMap<>();
        Map<String, MethodDef> annotationArrayMethods = new LinkedHashMap<>();
        Map<String, FieldDef> builderFields = new LinkedHashMap<>();
        Map<String, FieldDef> enumFields = new LinkedHashMap<>();

        ClassDef.ClassDefBuilder builder = ClassDef.builder(name)
            .synthetic()
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(annotationTypeDef)
            .addSuperinterface(ClassTypeDef.of(AnnotationValueProvider.class))
            .addAnnotation(AnnotationDef.builder(Generated.class).build())
            .addField(FieldDef.builder(ANNOTATION_VALUE_FIELD, ANNOTATION_VALUE_TYPE).addModifiers(Modifier.PRIVATE).build());

        for (Member member : members) {
            builder.addField(FieldDef.builder(member.name(), member.typeDef()).addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());
            if (member.component() instanceof EnumElement enumElement && !member.isClass()) {
                // the constants of an enum type, written out, to convert a name to a constant without reflection
                enumFields.computeIfAbsent(enumElement.getName(), key -> {
                    ClassTypeDef enumTypeDef = ClassTypeDef.of(key);
                    List<ExpressionDef> constants = new ArrayList<>();
                    for (String constant : enumElement.values()) {
                        constants.add(enumTypeDef.getStaticField(constant, enumTypeDef));
                    }
                    TypeDef.Array arrayType = enumTypeDef.array();
                    return FieldDef.builder("$constants_" + key.replace('.', '_').replace('$', '_'), arrayType)
                        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer(arrayType.instantiate(constants))
                        .build();
                });
            }
            if (member.isAnnotation()) {
                ClassElement annotation = member.component();
                // the builder of a nested annotation type is looked up once, when the class is initialized
                FieldDef builderField = builderFields.computeIfAbsent(annotation.getName(), key ->
                    FieldDef.builder("$builder_" + key.replace('.', '_').replace('$', '_'), ANNOTATION_BUILDER_TYPE)
                        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer(CONVERSION_UTILS.invokeStatic("annotationBuilder", List.of(TypeDef.CLASS), ANNOTATION_BUILDER_TYPE,
                            List.of(ExpressionDef.constant(ClassTypeDef.of(key)))))
                        .build());
                (member.isArray() ? annotationArrayMethods : annotationMethods).computeIfAbsent(annotation.getName(), key ->
                    member.isArray()
                        ? toAnnotationsMethod(annotation, annotationMethods.size() + annotationArrayMethods.size(), implementationType, builderField)
                        : toAnnotationMethod(annotation, annotationMethods.size() + annotationArrayMethods.size(), implementationType, builderField));
            }
        }

        builder.addMethod(MethodDef.constructor()
            .addModifiers(Modifier.PUBLIC)
            .addParameter("values", MAP_TYPE)
            .addParameter("defaults", MAP_TYPE)
            .addParameter("conversionService", CONVERSION_SERVICE_TYPE)
            .build((aThis, parameters) -> {
                List<StatementDef> statements = new ArrayList<>();
                statements.add(aThis.superRef().invokeConstructor());
                for (Member member : members) {
                    ExpressionDef raw = CONVERSION_UTILS.invokeStatic("member", TypeDef.OBJECT,
                        parameters.get(0), parameters.get(1), ExpressionDef.constant(member.name()));
                    ExpressionDef value = conversion(member, raw, parameters.get(2), implementationType, annotationMethods, annotationArrayMethods, enumFields);
                    statements.add(aThis.field(member.name(), member.typeDef()).assign(value));
                }
                return StatementDef.multi(statements);
            }));

        for (Member member : members) {
            builder.addMethod(accessor(member));
        }

        builder.addMethod(MethodDef.builder("annotationType")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.CLASS)
            .build((aThis, parameters) -> ExpressionDef.constant(annotationTypeDef).returning()));

        builder.addMethod(annotationValueMethod());
        builder.addMethod(buildAnnotationValueMethod(annotationTypeDef.getName(), members));

        builder.addMethod(hashCodeMethod(members));
        builder.addMethod(equalsMethod(annotationTypeDef, members));

        builder.addMethod(MethodDef.builder("toString")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.STRING)
            .build((aThis, parameters) -> aThis.invoke("annotationValue", ANNOTATION_VALUE_TYPE)
                .invoke("toString", TypeDef.STRING)
                .returning()));

        annotationMethods.values().forEach(builder::addMethod);
        annotationArrayMethods.values().forEach(builder::addMethod);
        builderFields.values().forEach(builder::addField);
        enumFields.values().forEach(builder::addField);
        return builder.build();
    }

    private static ExpressionDef conversion(Member member,
                                            ExpressionDef raw,
                                            ExpressionDef conversionService,
                                            ClassTypeDef implementationType,
                                            Map<String, MethodDef> annotationMethods,
                                            Map<String, MethodDef> annotationArrayMethods,
                                            Map<String, FieldDef> enumFields) {
        TypeDef type = member.typeDef();
        if (member.isPrimitive()) {
            String primitive = ((TypeDef.Primitive) type).name();
            String method = "to" + Character.toUpperCase(primitive.charAt(0)) + primitive.substring(1);
            return CONVERSION_UTILS.invokeStatic(method, type, raw, conversionService);
        }
        if (member.isClass()) {
            return CONVERSION_UTILS.invokeStatic(member.isArray() ? "toClasses" : "toClass", type, raw, conversionService);
        }
        if (member.isAnnotation()) {
            Map<String, MethodDef> methods = member.isArray() ? annotationArrayMethods : annotationMethods;
            return implementationType.invokeStatic(Objects.requireNonNull(methods.get(member.component().getName())), raw, conversionService);
        }
        if (!member.isArray() && String.class.getName().equals(member.component().getName())) {
            return CONVERSION_UTILS.invokeStatic("toString", TypeDef.STRING, raw, conversionService);
        }
        if (member.isArray()) {
            String component = member.component().getName();
            String simple = component.equals(String.class.getName()) ? "String" : null;
            if (member.typeDef() instanceof TypeDef.Array array && array.componentType() instanceof TypeDef.Primitive primitive) {
                simple = Character.toUpperCase(primitive.name().charAt(0)) + primitive.name().substring(1);
            }
            if (simple != null) {
                return CONVERSION_UTILS.invokeStatic("to" + simple + "Array", type, raw, conversionService);
            }
        }
        // an enum, or an array of enums: converted by name, among the constants of the enum type
        ExpressionDef constants = implementationType.getStaticField(
            Objects.requireNonNull(enumFields.get(member.component().getName())));
        ClassTypeDef enumType = ClassTypeDef.of(Enum.class);
        return member.isArray()
            ? CONVERSION_UTILS.invokeStatic("toEnumArray", List.of(TypeDef.OBJECT, enumType.array()), enumType.array(),
                List.of(raw, constants)).cast(type)
            : CONVERSION_UTILS.invokeStatic("toEnum", List.of(TypeDef.OBJECT, enumType.array()), enumType,
                List.of(raw, constants)).cast(type);
    }

    /**
     * {@code private static N toAnnotation_N(Object value, ConversionService conversionService)}.
     */
    private static MethodDef toAnnotationMethod(ClassElement annotation, int index, ClassTypeDef implementationType, FieldDef builderField) {
        TypeDef type = TypeDef.erasure(annotation);
        return MethodDef.builder("toAnnotation_" + methodSuffix(annotation, index))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(type)
            .addParameter("value", TypeDef.OBJECT)
            .addParameter("conversionService", CONVERSION_SERVICE_TYPE)
            .buildStatic(parameters -> CONVERSION_UTILS.invokeStatic("toAnnotation",
                    List.of(TypeDef.OBJECT, TypeDef.CLASS, ANNOTATION_BUILDER_TYPE, CONVERSION_SERVICE_TYPE), ClassTypeDef.of(Annotation.class),
                    List.of(parameters.get(0), ExpressionDef.constant(type), implementationType.getStaticField(builderField), parameters.get(1)))
                .cast(type)
                .returning());
    }

    /**
     * {@code private static N[] toAnnotations_N(Object value, ConversionService conversionService)}.
     */
    private static MethodDef toAnnotationsMethod(ClassElement annotation, int index, ClassTypeDef implementationType, FieldDef builderField) {
        TypeDef type = TypeDef.erasure(annotation);
        TypeDef.Array arrayType = type.array();
        return MethodDef.builder("toAnnotations_" + methodSuffix(annotation, index))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(arrayType)
            .addParameter("value", TypeDef.OBJECT)
            .addParameter("conversionService", CONVERSION_SERVICE_TYPE)
            .buildStatic(parameters -> CONVERSION_UTILS.invokeStatic("toAnnotations",
                    List.of(TypeDef.OBJECT, TypeDef.CLASS, ANNOTATION_BUILDER_TYPE, CONVERSION_SERVICE_TYPE), ClassTypeDef.of(List.class),
                    List.of(parameters.get(0), ExpressionDef.constant(type), implementationType.getStaticField(builderField), parameters.get(1)))
                .newLocal("list", list -> StatementDef.multi(
                    list.ifNull(ExpressionDef.nullValue().returning()),
                    list.invoke("toArray", List.of(OBJECT_ARRAY), OBJECT_ARRAY, List.of(arrayType.instantiate(List.of()))).cast(arrayType).returning())));
    }

    /**
     * {@code AnnotationValue annotationValue()}: the annotation value is created when it is first asked for, and
     * kept.
     */
    private static MethodDef annotationValueMethod() {
        return MethodDef.builder("annotationValue")
            .addModifiers(Modifier.PUBLIC)
            .returns(ANNOTATION_VALUE_TYPE)
            .build((aThis, parameters) -> {
                VariableDef.Field cache = aThis.field(ANNOTATION_VALUE_FIELD, ANNOTATION_VALUE_TYPE);
                return StatementDef.multi(
                    cache.ifNull(cache.assign(aThis.invoke("buildAnnotationValue", ANNOTATION_VALUE_TYPE))),
                    cache.returning());
            });
    }

    /**
     * {@code AnnotationValue buildAnnotationValue()}: the annotation value of the members, in the form the
     * annotation metadata records them. A member that is not set is left out.
     */
    private static MethodDef buildAnnotationValueMethod(String annotationName, List<Member> members) {
        return MethodDef.builder("buildAnnotationValue")
            .addModifiers(Modifier.PRIVATE)
            .returns(ANNOTATION_VALUE_TYPE)
            .build((aThis, parameters) -> ClassTypeDef.of(CollectionUtils.class)
                .invokeStatic("newLinkedHashMap", List.of(TypeDef.Primitive.INT), ClassTypeDef.of(LinkedHashMap.class),
                    List.of(ExpressionDef.constant(members.size())))
                .newLocal("members", map -> {
                List<StatementDef> statements = new ArrayList<>();
                for (Member member : members) {
                    ExpressionDef field = aThis.field(member.name(), member.typeDef());
                    StatementDef put = map.invoke("put", List.of(TypeDef.OBJECT, TypeDef.OBJECT), TypeDef.OBJECT,
                        List.of(ExpressionDef.constant(member.name()), memberValue(member, field)));
                    statements.add(member.isPrimitive() ? put : field.ifNonNull(put));
                }
                statements.add(ANNOTATION_VALUE_TYPE.instantiate(
                    List.of(TypeDef.STRING, MAP_TYPE), List.of(ExpressionDef.constant(annotationName), map)).returning());
                return StatementDef.multi(statements);
            }));
    }

    /**
     * The member in the form the annotation metadata records it.
     */
    private static ExpressionDef memberValue(Member member, ExpressionDef field) {
        TypeDef type = member.typeDef();
        if (member.isPrimitive()) {
            ClassTypeDef wrapper = ((TypeDef.Primitive) type).wrapperType();
            return wrapper.invokeStatic("valueOf", wrapper, field);
        }
        if (String.class.getName().equals(member.component().getName())
            || type instanceof TypeDef.Array array && array.componentType().isPrimitive()) {
            // a string, an array of strings and an array of primitives are recorded as they are
            return field;
        }
        if (member.isClass()) {
            return member.isArray()
                ? CONVERSION_UTILS.invokeStatic("toClassValues", List.of(TypeDef.CLASS.array()), ClassTypeDef.of(AnnotationClassValue.class).array(), List.of(field))
                : ClassTypeDef.of(AnnotationClassValue.class).instantiate(List.of(TypeDef.CLASS), List.of(field));
        }
        if (member.isAnnotation()) {
            // a nested annotation is one a builder created, which provides its annotation value
            return member.isArray()
                ? CONVERSION_UTILS.invokeStatic("toAnnotationValues", List.of(ClassTypeDef.of(Annotation.class).array()), ANNOTATION_VALUE_TYPE.array(), List.of(field))
                : field.cast(ClassTypeDef.of(AnnotationValueProvider.class)).invoke("annotationValue", ANNOTATION_VALUE_TYPE);
        }
        // an enum is recorded by the name of its constant
        return member.isArray()
            ? CONVERSION_UTILS.invokeStatic("toEnumNames", List.of(ClassTypeDef.of(Enum.class).array()), TypeDef.STRING.array(), List.of(field))
            : field.invoke("name", TypeDef.STRING);
    }

    /**
     * The hash code {@link Annotation#hashCode()} defines: the sum over the members of 127 times the hash of the
     * member name, exclusive-ored with the hash of the member value. The hashes of the names are computed here.
     */
    private static MethodDef hashCodeMethod(List<Member> members) {
        return MethodDef.builder("hashCode")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.Primitive.INT)
            .build((aThis, parameters) -> {
                ExpressionDef sum = ExpressionDef.constant(0);
                for (Member member : members) {
                    ExpressionDef nameHash = ExpressionDef.constant(127 * member.name().hashCode());
                    ExpressionDef field = aThis.field(member.name(), member.typeDef());
                    sum = sum.math(ExpressionDef.MathBinaryOperation.OpType.ADDITION,
                        nameHash.math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_XOR, memberHash(member, field)));
                }
                return sum.returning();
            });
    }

    private static ExpressionDef memberHash(Member member, ExpressionDef field) {
        TypeDef type = member.typeDef();
        if (member.isPrimitive()) {
            return ((TypeDef.Primitive) type).wrapperType().invokeStatic("hashCode", TypeDef.Primitive.INT, field);
        }
        if (member.isArray()) {
            boolean primitive = ((TypeDef.Array) type).componentType().isPrimitive();
            return ARRAYS.invokeStatic("hashCode", List.of(primitive ? type : OBJECT_ARRAY), TypeDef.Primitive.INT, List.of(field));
        }
        return OBJECTS.invokeStatic("hashCode", List.of(TypeDef.OBJECT), TypeDef.Primitive.INT, List.of(field));
    }

    /**
     * {@link Annotation#equals(Object)}: the other is an instance of the annotation type, and each member it
     * answers equals the member here. The members are read from the accessors of the other, so the other can be a
     * proxy of the annotation metadata or an annotation of the JVM as well as a generated one.
     */
    private static MethodDef equalsMethod(ClassTypeDef annotationTypeDef, List<Member> members) {
        return MethodDef.builder("equals")
            .addModifiers(Modifier.PUBLIC)
            .addParameter("other", TypeDef.OBJECT)
            .returns(TypeDef.Primitive.BOOLEAN)
            .build((aThis, parameters) -> {
                ExpressionDef object = parameters.get(0);
                return StatementDef.multi(
                    aThis.equalsReferentially(object).doIf(ExpressionDef.trueValue().returning()),
                    object.instanceOf(annotationTypeDef).isFalse().doIf(ExpressionDef.falseValue().returning()),
                    object.cast(annotationTypeDef).newLocal("annotation", annotation -> {
                        List<StatementDef> statements = new ArrayList<>();
                        for (Member member : members) {
                            TypeDef type = member.typeDef();
                            ExpressionDef field = aThis.field(member.name(), type);
                            ExpressionDef answer = annotation.invoke(member.name(), type);
                            statements.add(sameMember(member, field, answer).isFalse().doIf(ExpressionDef.falseValue().returning()));
                        }
                        statements.add(ExpressionDef.trueValue().returning());
                        return StatementDef.multi(statements);
                    }));
            });
    }

    private static ExpressionDef sameMember(Member member, ExpressionDef field, ExpressionDef answer) {
        TypeDef type = member.typeDef();
        if (member.isPrimitive()) {
            String name = ((TypeDef.Primitive) type).name();
            if ("float".equals(name) || "double".equals(name)) {
                // as the wrappers compare: NaN equals NaN
                return ((TypeDef.Primitive) type).wrapperType()
                    .invokeStatic("compare", TypeDef.Primitive.INT, field, answer)
                    .compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, ExpressionDef.constant(0));
            }
            return field.compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, answer);
        }
        if (member.isArray()) {
            boolean primitive = ((TypeDef.Array) type).componentType().isPrimitive();
            TypeDef parameter = primitive ? type : OBJECT_ARRAY;
            return ARRAYS.invokeStatic("equals", List.of(parameter, parameter), TypeDef.Primitive.BOOLEAN, List.of(field, answer));
        }
        return OBJECTS.invokeStatic("equals", List.of(TypeDef.OBJECT, TypeDef.OBJECT), TypeDef.Primitive.BOOLEAN, List.of(field, answer));
    }

    private static String methodSuffix(ClassElement annotation, int index) {
        String name = annotation.getName();
        return name.substring(name.lastIndexOf('.') + 1).replace('$', '_') + "_" + index;
    }

    private static MethodDef accessor(Member member) {
        TypeDef type = member.typeDef();
        return MethodDef.builder(member.name())
            .addModifiers(Modifier.PUBLIC)
            .returns(type)
            .build((aThis, parameters) -> {
                ExpressionDef field = aThis.field(member.name(), type);
                if (member.isArray()) {
                    // the arrays are copied, so that the caller cannot change the annotation
                    // return array == null ? null : array.clone();
                    return StatementDef.multi(
                        field.ifNull(ExpressionDef.nullValue().returning()),
                        field.invoke("clone", TypeDef.OBJECT).cast(type).returning());
                }
                return field.returning();
            });
    }

    private static ClassDef builder(String name,
                                    String implementationName,
                                    ClassElement annotationType,
                                    ClassTypeDef annotationTypeDef,
                                    List<Member> members,
                                    VisitorContext context) {
        Map<CharSequence, Object> defaults = new LinkedHashMap<>(context.getAnnotationDefaultValues(annotationType.getName()));
        ClassTypeDef builderTypeDef = ClassTypeDef.of(name);
        Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();
        ExpressionDef defaultsExpression = AnnotationMetadataGenUtils.valuesMapExpression(
            defaults, AnnotationMetadataGenUtils.createLoadClassValueExpressionFn(builderTypeDef, loadTypeMethods));

        ClassDef.ClassDefBuilder builder = ClassDef.builder(name)
            .synthetic()
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(ClassTypeDef.of(AbstractAnnotationBuilder.class))
            .addAnnotation(AnnotationDef.builder(Generated.class).build())
            .addMethod(MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .build((aThis, parameters) -> aThis.superRef().invokeConstructor(
                    ExpressionDef.constant(annotationTypeDef),
                    defaultsExpression)))
            .addMethod(MethodDef.builder("create")
                .addModifiers(Modifier.PROTECTED)
                .overrides()
                .addParameter("values", MAP_TYPE)
                .addParameter("defaults", MAP_TYPE)
                .addParameter("conversionService", CONVERSION_SERVICE_TYPE)
                .returns(ClassTypeDef.of(Annotation.class))
                .build((aThis, parameters) -> ClassTypeDef.of(implementationName)
                    .instantiate(List.of(MAP_TYPE, MAP_TYPE, CONVERSION_SERVICE_TYPE),
                        parameters.get(0), parameters.get(1), parameters.get(2))
                    .returning()));
        loadTypeMethods.values().forEach(builder::addMethod);
        return builder.build();
    }
}

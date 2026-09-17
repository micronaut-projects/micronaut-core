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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.AnnotationMap;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.InterfaceDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/** Generates a bounded String/int annotation schema as a typed, Map-compatible view. */
@Internal
@SupportedAnnotationTypes("io.micronaut.core.annotation.GenerateAnnotationMap")
public final class AnnotationMapProcessor extends AbstractProcessor {
    private static final String MARKER = "io.micronaut.core.annotation.GenerateAnnotationMap";
    private static final ClassTypeDef MEMBER_MAP = TypeDef.parameterized(Map.class, CharSequence.class, Object.class);
    private static final ClassTypeDef ANNOTATION_VALUE = ClassTypeDef.of(AnnotationValue.class);
    private static final Method DELEGATE = ReflectionUtils.getRequiredMethod(AnnotationMap.class, "getDelegate");
    private static final Method GET = ReflectionUtils.getRequiredMethod(Map.class, "get", Object.class);
    private static final Method GET_OR_DEFAULT = ReflectionUtils.getRequiredMethod(Map.class, "getOrDefault", Object.class, Object.class);
    private static final Set<String> RESERVED = Arrays.stream(Map.class.getMethods()).map(Method::getName).collect(Collectors.toSet());

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement marker = processingEnv.getElementUtils().getTypeElement(MARKER);
        if (marker != null) {
            for (var element : round.getElementsAnnotatedWith(marker)) {
                if (element.getKind() != ElementKind.ANNOTATION_TYPE) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Expected an annotation schema", element);
                } else {
                    generate((TypeElement) element);
                }
            }
        }
        return false;
    }

    private void generate(TypeElement element) {
        if (element.getNestingKind() != NestingKind.TOP_LEVEL) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "The annotation-map prototype requires a top-level schema", element);
            return;
        }
        String annotationName = element.getQualifiedName().toString();
        String name = annotationName + "$AnnotationMap";
        ClassTypeDef viewType = InterfaceDef.builder(name).build().asTypeDef();
        ClassTypeDef adapterType = ClassTypeDef.of(name + "Adapter");
        ClassTypeDef storedType = ClassTypeDef.of(name + "Stored");
        ClassTypeDef parent = ClassTypeDef.of(AnnotationMap.class);
        var view = InterfaceDef.builder(name).addModifiers(Modifier.PUBLIC).addAnnotation(Experimental.class).addSuperinterface(MEMBER_MAP);
        var adapter = ClassDef.builder(adapterType.getName()).addModifiers(Modifier.FINAL)
            .superclass(parent).addSuperinterface(viewType);
        var stored = ClassDef.builder(storedType.getName()).addModifiers(Modifier.FINAL)
            .superclass(parent).addSuperinterface(viewType);
        FieldDef annotation = FieldDef.builder("annotation", ANNOTATION_VALUE)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build();
        adapter.addField(annotation);
        adapter.addMethod(MethodDef.constructor().addParameter("map", MEMBER_MAP).build((self, params) -> StatementDef.multi(
            self.superRef().invokeSuperConstructor(params.getFirst(), ExpressionDef.constant(false)),
            self.field(annotation).assign(ANNOTATION_VALUE.instantiate(ExpressionDef.constant(annotationName), params.getFirst()))
        )));
        List<Member> members = new ArrayList<>();
        Set<String> accessors = new HashSet<>();
        for (var enclosed : element.getEnclosedElements()) {
            if (!(enclosed instanceof ExecutableElement method)) {
                continue;
            }
            String typeName = method.getReturnType().toString();
            boolean string = typeName.equals(String.class.getName());
            if (!string && !typeName.equals("int")) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "The annotation-map prototype supports only String and int members", method);
                return;
            }
            var defaultValue = method.getDefaultValue();
            if (defaultValue == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "The annotation-map prototype requires a default for each member", method);
                return;
            }
            if (defaultValue.getValue() instanceof String text && (text.contains("${") || text.contains("#{"))) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "The annotation-map prototype requires literal defaults without expressions", method);
                return;
            }
            TypeDef type = string ? TypeDef.STRING : TypeDef.Primitive.INT;
            String memberName = method.getSimpleName().toString();
            String accessor = (RESERVED.contains(memberName) || memberName.equals("getDelegate")) ? "member_" + memberName : memberName;
            if (!accessors.add(accessor)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Annotation member accessor name collision: " + accessor, method);
                return;
            }
            ExpressionDef fallback = ExpressionDef.constant(defaultValue.getValue());
            FieldDef field = FieldDef.builder("cached_" + memberName, type).addModifiers(Modifier.PRIVATE, Modifier.FINAL).build();
            members.add(new Member(memberName, field, fallback));
            Method resolve = ReflectionUtils.getRequiredMethod(AnnotationValue.class, string ? "stringValue" : "intValue", String.class);
            Method orElse = ReflectionUtils.getRequiredMethod(string ? Optional.class : OptionalInt.class,
                "orElse", string ? Object.class : int.class);
            // Default getters let older generated implementations inherit reads of newly added members.
            view.addMethod(MethodDef.builder(accessor).addModifiers(Modifier.PUBLIC, Modifier.DEFAULT).returns(type)
                .build((self, params) -> ANNOTATION_VALUE.instantiate(ExpressionDef.constant(annotationName), self.cast(MEMBER_MAP))
                    .invoke(resolve, ExpressionDef.constant(memberName)).invoke(orElse, fallback).cast(type).returning()));
            adapter.addMethod(MethodDef.builder(accessor).addModifiers(Modifier.PUBLIC).returns(type)
                .build((self, params) -> self.field(annotation).invoke(resolve, ExpressionDef.constant(memberName))
                    .invoke(orElse, fallback).cast(type).returning()));
            stored.addField(field);
            stored.addMethod(MethodDef.builder(accessor).addModifiers(Modifier.PUBLIC).returns(type)
                .build((self, params) -> self.field(field).returning()));
        }
        stored.addMethod(MethodDef.constructor().addParameter("map", MEMBER_MAP).build((self, params) -> {
            List<StatementDef> statements = new ArrayList<>();
            statements.add(self.superRef().invokeSuperConstructor(params.getFirst(), ExpressionDef.constant(true)));
            for (Member member : members) {
                statements.add(self.field(member.field).assign(self.invoke(DELEGATE).invoke(GET_OR_DEFAULT,
                    ExpressionDef.constant(member.name), member.fallback).cast(member.field.getType())));
            }
            return StatementDef.multi(statements);
        }));
        view.addMethod(MethodDef.builder("of").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter("map", MEMBER_MAP).returns(viewType).buildStatic(params -> StatementDef.multi(
                params.getFirst().instanceOf(viewType).doIf(params.getFirst().cast(viewType).returning()),
                adapterType.instantiate(params.getFirst()).returning())));
        // Explicit snapshot factory: generated-style scalar values only; of(map) never snapshots a mutable map.
        view.addMethod(MethodDef.builder("immutable").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter("map", MEMBER_MAP).returns(viewType).buildStatic(params ->
                storedType.instantiate(params.getFirst()).returning()));
        // Metadata can contain mapped values or expression objects. Keep those on the ordinary path.
        view.addMethod(MethodDef.builder("create").addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(Internal.class).addAnnotation(UsedByGeneratedCode.class)
            .addParameter("map", MEMBER_MAP).returns(MEMBER_MAP).buildStatic(params -> {
                List<StatementDef> statements = new ArrayList<>();
                for (Member member : members) {
                    var value = params.getFirst().invoke(GET, ExpressionDef.constant(member.name));
                    ClassTypeDef expected = member.field.getType().equals(TypeDef.STRING) ? ClassTypeDef.of(String.class) : ClassTypeDef.of(Integer.class);
                    statements.add(value.isNonNull().and(value.instanceOf(expected).isFalse())
                        .doIf(params.getFirst().returning()));
                }
                statements.add(storedType.instantiate(params.getFirst()).returning());
                return StatementDef.multi(statements);
            }));
        for (var model : List.of(view.build(), adapter.build(), stored.build())) {
            try (var output = processingEnv.getFiler().createClassFile(model.getName(), element).openOutputStream()) {
                output.write(ByteCodeWriterUtils.writeByteCode(model, null));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot generate annotation map", exception);
            }
        }
    }

    private record Member(String name, FieldDef field, ExpressionDef fallback) {
    }
}

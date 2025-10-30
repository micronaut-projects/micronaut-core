package io.micronaut.python.processing;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

public class PythonStubGenerator implements TypeElementVisitor<Object, Object> {

    public static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element instanceof AbstractPythonClassElement) {
            SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(VisitorContext.Language.JAVA).orElse(null);
            if (sourceGenerator != null) {
                try {
                    String typeName = element.getName();
                    var builder = ClassDef.builder(typeName);
                    FieldDef pythonValue = FieldDef.builder("graalpyInternalValue")
                        .ofType(POLYGLOT_VALUE).addModifiers(Modifier.FINAL, Modifier.PRIVATE).build();
                    builder.addField(pythonValue);
                    // add constructor that looks up value
                    MethodDef.MethodDefBuilder constructor = MethodDef.constructor();
                    builder.addMethod(
                        constructor.build(((aThis, methodParameters) -> aThis.field(pythonValue).assign(
                            ClassTypeDef.of("io.micronaut.context.python.ContextHolder")
                                .invokeStatic("getContext", TypeDef.of(Context.class))
                                .invoke("getBindings", POLYGLOT_VALUE, ExpressionDef.constant("python"))
                                .invoke("getMember", POLYGLOT_VALUE, ExpressionDef.constant(element.getSimpleName()))
                                .invoke("newInstance", POLYGLOT_VALUE)
                        )))
                    );

                    List<MethodElement> methodsToBridge = element.getEnclosedElements(
                        ElementQuery.ALL_METHODS
                            .onlyAccessible()
                            .onlyInstance()
                            .annotated(ann -> ann.hasStereotype(Executable.class)));


                    for (MethodElement methodElement : methodsToBridge) {
                        String pythonFunctionName = methodElement.getName();
                        String javaName = NameUtils.camelCase(pythonFunctionName);
                        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(javaName)
                            .returns(TypeDef.of(methodElement.getReturnType()));

                        for (@NonNull ParameterElement parameter : methodElement.getParameters()) {
                            methodBuilder.addParameter(ParameterDef.builder(parameter.getName(), TypeDef.of(parameter.getType())).build());
                        }

                        builder.addMethod(methodBuilder
                            .build(((aThis, methodParameters) -> {
                                VariableDef.Field pythonValueFIeld = aThis.field(pythonValue);
                                List<ExpressionDef> parameters = new ArrayList<>();
                                parameters.add(ExpressionDef.constant(pythonFunctionName));
                                parameters.addAll(methodParameters);
                                return pythonValueFIeld.invoke(
                                    "invokeMember",
                                    POLYGLOT_VALUE,
                                    parameters
                                ).invoke("asString", ClassTypeDef.STRING).returning();
                            })));
                    }

                    sourceGenerator.write(builder.build(), context, element);
                } catch (Exception e) {
                    context.fail("Failed to generate stub for Python type [" + element.getSimpleName() + "]: " + e.getMessage(), null);
                }

            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}

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
package io.micronaut.context.python.runtime;

import io.micronaut.context.python.runtime.generate.PythonMetadataClassGenerator;
import io.micronaut.context.python.runtime.model.AnnotationMetadataModel;
import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.BeanMethodModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.ConstructorModel;
import io.micronaut.context.python.runtime.model.ExecutableMethodModel;
import io.micronaut.context.python.runtime.model.FactoryMethodModel;
import io.micronaut.context.python.runtime.model.InjectedMethodModel;
import io.micronaut.context.python.runtime.model.InjectionPointModel;
import io.micronaut.context.python.runtime.model.IntrospectionModel;
import io.micronaut.context.python.runtime.model.MethodModel;
import io.micronaut.context.python.runtime.model.PrecalculatedInfoModel;
import io.micronaut.context.python.runtime.model.PropertyIndexModel;
import io.micronaut.context.python.runtime.model.PropertyModel;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonMetadataClassGeneratorTest {

    static final AnnotationMetadataModel EMPTY = AnnotationMetadataModel.EMPTY;

    static ArgumentModel arg(String name, String type) {
        return new ArgumentModel(name, type, EMPTY, List.of());
    }

    static MethodModel method(String name, String returnType, ArgumentModel... parameters) {
        return new MethodModel(SampleBean.class.getName(), name, arg(name, returnType), List.of(parameters), EMPTY, false);
    }

    static PrecalculatedInfoModel singleton() {
        return new PrecalculatedInfoModel("jakarta.inject.Singleton", false, false, true, false, false, false);
    }

    /**
     * A definition of {@link SampleBean} instantiated by its constructor.
     */
    static BeanDefinitionModel definition(ConstructorModel constructor, List<InjectedMethodModel> methods, List<ExecutableMethodModel> executableMethods) {
        return new BeanDefinitionModel("io.micronaut.context.python.runtime.$SampleBean$Definition", SampleBean.class.getName(), null, null, null,
            constructor, methods, executableMethods, singleton(), List.of(SampleBean.class.getName()), false, Map.of(), false, false);
    }

    /**
     * A definition of {@link SampleDependency} produced by a method of {@link SampleFactory}.
     */
    static BeanDefinitionModel factoryDefinition(String methodName, int index, boolean isStatic, List<ArgumentModel> parameters,
                                                 List<InjectionPointModel> injectionPoints) {
        FactoryMethodModel factory = new FactoryMethodModel(SampleFactory.class.getName(), methodName,
            arg(methodName, SampleDependency.class.getName()), isStatic);
        return new BeanDefinitionModel("io.micronaut.context.python.runtime.$SampleFactory$" + methodName.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
            + methodName.substring(1) + index + "$Definition", SampleDependency.class.getName(), factory, EMPTY, EMPTY,
            new ConstructorModel(EMPTY, parameters, injectionPoints), List.of(), List.of(), singleton(),
            List.of(SampleDependency.class.getName()), false, Map.of(), false, false);
    }

    static ClassModel sample() {
        ArgumentModel dependency = arg("dependency", SampleDependency.class.getName());
        ArgumentModel value = arg("value", "java.lang.String");
        ConstructorModel constructor = new ConstructorModel(EMPTY, List.of(dependency, value), List.of(
            new InjectionPointModel(InjectionPointModel.Kind.BEAN, dependency, null, null, null, null, null),
            new InjectionPointModel(InjectionPointModel.Kind.VALUE, value, null, null, null, "${sample.value:x}", null)));
        ArgumentModel other = arg("other", SampleDependency.class.getName());
        ArgumentModel all = arg("all", "java.util.List");
        List<InjectedMethodModel> methods = List.of(
            new InjectedMethodModel(method("setOther", "void", other), EMPTY,
                List.of(new InjectionPointModel(InjectionPointModel.Kind.BEAN, other, null, null, null, null, null)), false, false, false, false, true, null),
            new InjectedMethodModel(method("setAll", "void", all), EMPTY,
                List.of(new InjectionPointModel(InjectionPointModel.Kind.BEANS, all, SampleDependency.class.getName(), null, null, null, null)), false, false, false, false, false, null),
            new InjectedMethodModel(method("initialize", "void"), EMPTY, List.of(), false, false, true, false, true, null),
            new InjectedMethodModel(method("close", "boolean"), EMPTY, List.of(), false, false, false, true, true, null));
        BeanDefinitionModel definition = definition(constructor, methods,
            List.of(new ExecutableMethodModel(method("getName", "java.lang.String"), arg("getName", "java.lang.String"), EMPTY, true, true, false),
                new ExecutableMethodModel(method("setAge", "void", arg("age", "int")), arg("setAge", "void"), EMPTY, true, false, false)));
        List<PropertyModel> properties = List.of(
            new PropertyModel("name", arg("name", "java.lang.String"), method("getName", "java.lang.String"), method("setName", "void", arg("name", "java.lang.String")), false),
            new PropertyModel("age", arg("age", "int"), method("getAge", "int"), method("setAge", "void", arg("age", "int")), false),
            new PropertyModel("id", arg("id", "long"), method("getId", "long"), null, true));
        IntrospectionModel introspection = new IntrospectionModel("io.micronaut.context.python.runtime.$SampleBean$Introspection", EMPTY, List.of(dependency, value), properties,
            List.of(new PropertyIndexModel("jakarta.validation.Constraint", null, 0), new PropertyIndexModel("jakarta.persistence.Column", "name_col", 0),
                new PropertyIndexModel("jakarta.persistence.Column", "age_col", 1)),
            List.of(new BeanMethodModel(method("initialize", "void"), arg("initialize", "void"), EMPTY),
                new BeanMethodModel(method("setAge", "void", arg("age", "int")), arg("setAge", "void"), EMPTY)), null, null);
        return new ClassModel(SampleBean.class.getName(), EMPTY, List.of(definition), introspection);
    }

    static String verify(byte[] bytes, byte[]... companions) {
        Map<String, byte[]> generated = new java.util.HashMap<>();
        for (byte[] companion : companions) {
            generated.put(new ClassReader(companion).getClassName().replace('/', '.'), companion);
        }
        ClassLoader loader = new ClassLoader(PythonMetadataClassGeneratorTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] companion = generated.get(name);
                if (companion == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, companion, 0, companion.length);
            }
        };
        StringWriter writer = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(bytes), loader, false, new PrintWriter(writer));
        return writer.toString();
    }

    @Test
    void generatesMinimalDefinition() {
        BeanDefinitionModel definition = definition(new ConstructorModel(EMPTY, List.of(), List.of()), List.of(), List.of());
        String report = verify(PythonMetadataClassGenerator.beanDefinition(new ClassModel(SampleBean.class.getName(), EMPTY, List.of(definition), null), definition));
        assertTrue(report.isEmpty(), report);
    }

    @Test
    void generatesConstructorInjection() {
        ArgumentModel dependency = arg("dependency", SampleDependency.class.getName());
        ConstructorModel constructor = new ConstructorModel(EMPTY, List.of(dependency), List.of(
            new InjectionPointModel(InjectionPointModel.Kind.BEAN, dependency, null, null, null, null, null)));
        BeanDefinitionModel definition = definition(constructor, List.of(), List.of());
        String report = verify(PythonMetadataClassGenerator.beanDefinition(new ClassModel(SampleBean.class.getName(), EMPTY, List.of(definition), null), definition));
        assertTrue(report.isEmpty(), report);
    }

    @Test
    void generatesMethodInjection() {
        ArgumentModel other = arg("other", SampleDependency.class.getName());
        List<InjectedMethodModel> methods = List.of(
            new InjectedMethodModel(method("setOther", "void", other), EMPTY,
                List.of(new InjectionPointModel(InjectionPointModel.Kind.BEAN, other, null, null, null, null, null)), false, false, false, false, true, null));
        BeanDefinitionModel definition = definition(new ConstructorModel(EMPTY, List.of(), List.of()), methods, List.of());
        String report = verify(PythonMetadataClassGenerator.beanDefinition(new ClassModel(SampleBean.class.getName(), EMPTY, List.of(definition), null), definition));
        assertTrue(report.isEmpty(), report);
    }

    @Test
    void generatesVerifiableDefinitionAndIntrospection() {
        ClassModel model = sample();
        BeanDefinitionModel definitionModel = model.beanDefinitions().getFirst();
        byte[] definition = PythonMetadataClassGenerator.beanDefinition(model, definitionModel);
        byte[] introspection = PythonMetadataClassGenerator.introspection(model);
        byte[] executable = PythonMetadataClassGenerator.executableMethods(model, definitionModel);
        String definitionReport = verify(definition, executable);
        String introspectionReport = verify(introspection);
        String executableReport = verify(executable);
        assertTrue(definitionReport.isEmpty(), definitionReport);
        assertTrue(introspectionReport.isEmpty(), introspectionReport);
        assertTrue(executableReport.isEmpty(), executableReport);
    }

    @Test
    void generatesFactoryDefinitions() {
        ArgumentModel value = arg("value", "java.lang.String");
        BeanDefinitionModel instanceMade = factoryDefinition("instanceMade", 0, false, List.of(value),
            List.of(new InjectionPointModel(InjectionPointModel.Kind.VALUE, value, null, null, null, "${sample.value:x}", null)));
        BeanDefinitionModel staticMade = factoryDefinition("staticMade", 1, true, List.of(), List.of());
        ClassModel model = new ClassModel(SampleFactory.class.getName(), EMPTY, List.of(instanceMade, staticMade), null);
        String instanceReport = verify(PythonMetadataClassGenerator.beanDefinition(model, instanceMade));
        String staticReport = verify(PythonMetadataClassGenerator.beanDefinition(model, staticMade));
        assertTrue(instanceReport.isEmpty(), instanceReport);
        assertTrue(staticReport.isEmpty(), staticReport);
    }

    @Test
    void generationIsDeterministic() {
        assertArrayEquals(PythonMetadataClassGenerator.beanDefinition(sample(), sample().beanDefinitions().getFirst()),
            PythonMetadataClassGenerator.beanDefinition(sample(), sample().beanDefinitions().getFirst()));
        assertArrayEquals(PythonMetadataClassGenerator.introspection(sample()), PythonMetadataClassGenerator.introspection(sample()));
    }
}

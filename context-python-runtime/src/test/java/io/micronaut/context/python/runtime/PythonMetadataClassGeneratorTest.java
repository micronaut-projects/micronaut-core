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
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.ConstructorModel;
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

    static ClassModel sample() {
        ArgumentModel dependency = arg("dependency", SampleDependency.class.getName());
        ArgumentModel value = arg("value", "java.lang.String");
        ConstructorModel constructor = new ConstructorModel(EMPTY, List.of(dependency, value), List.of(
            new InjectionPointModel(InjectionPointModel.Kind.BEAN, dependency, null, null, null, null),
            new InjectionPointModel(InjectionPointModel.Kind.VALUE, value, null, null, null, "${sample.value:x}")));
        ArgumentModel other = arg("other", SampleDependency.class.getName());
        ArgumentModel all = arg("all", "java.util.List");
        List<InjectedMethodModel> methods = List.of(
            new InjectedMethodModel(method("setOther", "void", other), EMPTY,
                List.of(new InjectionPointModel(InjectionPointModel.Kind.BEAN, other, null, null, null, null)), false, false, false, false, true),
            new InjectedMethodModel(method("setAll", "void", all), EMPTY,
                List.of(new InjectionPointModel(InjectionPointModel.Kind.BEANS, all, SampleDependency.class.getName(), null, null, null)), false, false, false, false, false),
            new InjectedMethodModel(method("initialize", "void"), EMPTY, List.of(), false, false, true, false, true),
            new InjectedMethodModel(method("close", "boolean"), EMPTY, List.of(), false, false, false, true, true));
        BeanDefinitionModel definition = new BeanDefinitionModel("io.micronaut.context.python.runtime.$SampleBean$Definition", constructor, methods,
            new PrecalculatedInfoModel("jakarta.inject.Singleton", false, false, true, false, false, false), List.of(SampleBean.class.getName()), false, Map.of());
        List<PropertyModel> properties = List.of(
            new PropertyModel("name", arg("name", "java.lang.String"), method("getName", "java.lang.String"), method("setName", "void", arg("name", "java.lang.String")), false),
            new PropertyModel("age", arg("age", "int"), method("getAge", "int"), method("setAge", "void", arg("age", "int")), false),
            new PropertyModel("id", arg("id", "long"), method("getId", "long"), null, true));
        IntrospectionModel introspection = new IntrospectionModel("io.micronaut.context.python.runtime.$SampleBean$Introspection", EMPTY, List.of(dependency, value), properties,
            List.of(new PropertyIndexModel("jakarta.validation.Constraint", null, 0), new PropertyIndexModel("jakarta.persistence.Column", "name_col", 0),
                new PropertyIndexModel("jakarta.persistence.Column", "age_col", 1)));
        return new ClassModel(SampleBean.class.getName(), EMPTY, definition, introspection);
    }

    static String verify(byte[] bytes) {
        StringWriter writer = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(bytes), PythonMetadataClassGeneratorTest.class.getClassLoader(), false, new PrintWriter(writer));
        return writer.toString();
    }

    @Test
    void generatesMinimalDefinition() {
        BeanDefinitionModel definition = new BeanDefinitionModel("io.micronaut.context.python.runtime.$SampleBean$Definition", new ConstructorModel(EMPTY, List.of(), List.of()), List.of(),
            new PrecalculatedInfoModel("jakarta.inject.Singleton", false, false, true, false, false, false), List.of(SampleBean.class.getName()), false, Map.of());
        String report = verify(PythonMetadataClassGenerator.beanDefinition(new ClassModel(SampleBean.class.getName(), EMPTY, definition, null)));
        assertTrue(report.isEmpty(), report);
    }

    @Test
    void generatesConstructorInjection() {
        ArgumentModel dependency = arg("dependency", SampleDependency.class.getName());
        ConstructorModel constructor = new ConstructorModel(EMPTY, List.of(dependency), List.of(
            new InjectionPointModel(InjectionPointModel.Kind.BEAN, dependency, null, null, null, null)));
        BeanDefinitionModel definition = new BeanDefinitionModel("io.micronaut.context.python.runtime.$SampleBean$Definition", constructor, List.of(),
            new PrecalculatedInfoModel("jakarta.inject.Singleton", false, false, true, false, false, false), List.of(SampleBean.class.getName()), false, Map.of());
        String report = verify(PythonMetadataClassGenerator.beanDefinition(new ClassModel(SampleBean.class.getName(), EMPTY, definition, null)));
        assertTrue(report.isEmpty(), report);
    }

    @Test
    void generatesMethodInjection() {
        ArgumentModel other = arg("other", SampleDependency.class.getName());
        List<InjectedMethodModel> methods = List.of(
            new InjectedMethodModel(method("setOther", "void", other), EMPTY,
                List.of(new InjectionPointModel(InjectionPointModel.Kind.BEAN, other, null, null, null, null)), false, false, false, false, true));
        BeanDefinitionModel definition = new BeanDefinitionModel("io.micronaut.context.python.runtime.$SampleBean$Definition", new ConstructorModel(EMPTY, List.of(), List.of()), methods,
            new PrecalculatedInfoModel("jakarta.inject.Singleton", false, false, true, false, false, false), List.of(SampleBean.class.getName()), false, Map.of());
        String report = verify(PythonMetadataClassGenerator.beanDefinition(new ClassModel(SampleBean.class.getName(), EMPTY, definition, null)));
        assertTrue(report.isEmpty(), report);
    }

    @Test
    void generatesVerifiableDefinitionAndIntrospection() {
        ClassModel model = sample();
        byte[] definition = PythonMetadataClassGenerator.beanDefinition(model);
        byte[] introspection = PythonMetadataClassGenerator.introspection(model);
        String definitionReport = verify(definition);
        String introspectionReport = verify(introspection);
        assertTrue(definitionReport.isEmpty(), definitionReport);
        assertTrue(introspectionReport.isEmpty(), introspectionReport);
    }

    @Test
    void generationIsDeterministic() {
        assertArrayEquals(PythonMetadataClassGenerator.beanDefinition(sample()), PythonMetadataClassGenerator.beanDefinition(sample()));
        assertArrayEquals(PythonMetadataClassGenerator.introspection(sample()), PythonMetadataClassGenerator.introspection(sample()));
    }
}

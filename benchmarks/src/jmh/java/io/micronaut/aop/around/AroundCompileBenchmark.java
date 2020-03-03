/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.aop.around;

import io.micronaut.annotation.processing.test.JavaParser;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Objects;

@State(Scope.Benchmark)
public class AroundCompileBenchmark {

    StringBuilder source = new StringBuilder();


    @Setup
    public void prepare() {
        source.append("package test;\n" +
                "\n" +
                "import javax.inject.Singleton;\n" +
                "import javax.sql.DataSource;\n" +
                "import io.micronaut.validation.Validated;\n" +
                "\n" +
                "@Singleton\n" +
                "@Validated\n" +
                "public class Test {\n" );

        for (int i = 0; i < 1000; i++) {
             source.append("\npublic void insert")
                     .append(i)
                     .append("(int i) {\n")
                     .append("        System.out.println(\"hello\" + i);\n")
                     .append("    };");

        }
        source.append("}");
    }

    @Benchmark
    public void benchmarkCompileAround() {
        final BeanDefinition beanDefinition = buildBeanDefinition("test.Test", source.toString());
        Objects.requireNonNull(beanDefinition);
    }

    BeanDefinition buildBeanDefinition(String className, String cls) {
        String beanDefName= '$' + NameUtils.getSimpleName(className) + "Definition";
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + "." + beanDefName;

        ClassLoader classLoader = buildClassLoader(className, cls);
        return (BeanDefinition) InstantiationUtils.instantiate(beanFullName, classLoader);
    }

    protected ClassLoader buildClassLoader(String className, String cls) {
        final Iterable<? extends JavaFileObject> files = newJavaParser().generate(className, cls);
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String fileName = name.replace('.', '/') + ".class";
                JavaFileObject generated = CollectionUtils.iterableToList(files)
                    .stream().filter((it) -> it.getName().endsWith(fileName))
                    .findFirst().orElse(null);
                try {
                    if (generated != null) {
                        byte[] bytes = IOGroovyMethods.getBytes(generated.openInputStream());
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Compile failed: " + e.getMessage());
                }
                return super.findClass(name);
            }
        };
        return classLoader;
    }

    /**
     * Create and return a new Java parser.
     * @return The java parser to use
     */
    protected JavaParser newJavaParser() {
        return new JavaParser();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + AroundCompileBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(3)
                .measurementIterations(5)
                .forks(1)
//                .jvmArgs("-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib")
                .build();

        new Runner(opt).run();
    }

}

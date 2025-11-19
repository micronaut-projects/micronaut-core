/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.internal.python;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import static org.apache.groovy.util.BeanUtils.capitalize;

/**
 * Defines tasks and configuration required for compiling Python
 * sources.
 */
public class PyronautCompilerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Pyronaut generates Java classes so we need the Java plugin
        project.getPluginManager().apply("java");
        var pyronautCompiler = createPyronautCompilerConfiguration(project);
        var pyronautCompilerClasspath = createPyronautCompilerClasspath(project, pyronautCompiler);
        configureSourceSet(project, pyronautCompilerClasspath);
    }

    /**
     * Creates a resolvable configuration which resolves the Pyronaut compiler.
     *
     * @param project the project
     * @param pyronautCompiler the configuration which declares the compiler dependencies
     * @return the resolvable configuration
     */
    private static Configuration createPyronautCompilerClasspath(Project project,
                                                                 Configuration pyronautCompiler) {
        return project.getConfigurations().create("pyronautCompilerClasspath", conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
            conf.extendsFrom(pyronautCompiler);
            conf.attributes(attrs -> {
                var objects = project.getObjects();
                attrs.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.class, LibraryElements.JAR));
                attrs.attribute(Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category.class, Category.LIBRARY));
                attrs.attribute(Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });
    }

    /**
     * Creates the configuration used to declare the dependencies of the Pyronaut compiler
     *
     * @param project the project
     * @return the compiler configuration
     */
    private static Configuration createPyronautCompilerConfiguration(Project project) {
        return project.getConfigurations().create("pyronautCompiler", conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(false);
        });
    }

    /**
     * Generates a Python source directory set and creates a compilation task for
     * each source set.
     *
     * @param project the project
     * @param pyronautCompilerClasspath the compiler classpath configuration
     */
    private static void configureSourceSet(Project project,
                                           Configuration pyronautCompilerClasspath) {
        project.getPluginManager().withPlugin("java-base", unused -> {
            var sourceSets = project.getExtensions().findByType(SourceSetContainer.class);
            sourceSets.all(sourceSet -> createPythonSourceDirectory(project, pyronautCompilerClasspath, sourceSet));
        });
    }

    private static void createPythonSourceDirectory(Project project,
                                                    Configuration pyronautCompilerClasspath,
                                                    SourceSet sourceSet) {
        var sourceDirectorySet =
            project.getObjects().sourceDirectorySet("python", "Python sources");
        sourceSet.getExtensions().add("python", sourceDirectorySet);
        var sourceSetName = sourceSet.getName();
        sourceDirectorySet.srcDir("src/" + sourceSetName + "/python");
        var layout = project.getLayout();
        var taskName = "compilePython";
        if (!SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSetName)) {
            taskName = "compile" + capitalize(sourceSetName) + "Python";
        }
        var compileTask =
            createCompileTask(project, pyronautCompilerClasspath, sourceSet, taskName, sourceDirectorySet);
        var classesDirs = sourceSet.getOutput().getClassesDirs();
        if (classesDirs instanceof ConfigurableFileCollection cfc) {
            // Declare that the Python compiler task contributes new classes
            cfc.from(compileTask.flatMap(PythonCompile::getDestinationDir));
        } else {
            throw new IllegalStateException(
                "Unexpected classes directory type: " + classesDirs.getClass());
        }
    }

    /**
     * Creates a new Pyronaut compilation task.
     *
     * @param project the project
     * @param pyronautCompilerClasspath the compiler classpath
     * @param sourceSet the source set for which to generate a compilation task
     * @param taskName the name of the task to create
     * @param sourceDirectorySet the Python source directory set
     */
    private static TaskProvider<PythonCompile> createCompileTask(Project project,
                                                                 Configuration pyronautCompilerClasspath,
                                                                 SourceSet sourceSet,
                                                                 String taskName,
                                                                 SourceDirectorySet sourceDirectorySet) {
        return project.getTasks().register(taskName, PythonCompile.class, task -> {
            task.getSource().convention(sourceDirectorySet.getSourceDirectories());
            task.getDestinationDir()
                .convention(project.getLayout().getBuildDirectory().dir("classes/python/" + sourceSet.getName()));
            task.getClasspath().from(pyronautCompilerClasspath);
            task.getClasspath().from(project.getConfigurations()
                .getByName(sourceSet.getCompileClasspathConfigurationName()));
        });
    }
}

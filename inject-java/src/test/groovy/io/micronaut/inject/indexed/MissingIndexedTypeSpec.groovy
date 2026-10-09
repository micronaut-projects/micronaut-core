package io.micronaut.inject.indexed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaFileObjects
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.writer.BeanDefinitionWriter

import java.io.IOException
import java.io.InputStream
import java.util.HashMap
import javax.tools.JavaFileObject

class MissingIndexedTypeSpec extends AbstractTypeElementSpec {

    void "test a bean definition loads when an indexed type is absent at runtime"() {
        given:
        JavaFiles sources = new JavaFiles()
                .add('optional.MissingIndex', '''
package optional;

public interface MissingIndex {
}
''')
                .add('optional.AvailableIndex', '''
package optional;

public interface AvailableIndex {
}
''')
                .add('optional.Bean', '''
package optional;

import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;

@Singleton
@Indexed(MissingIndex.class)
@Indexed(AvailableIndex.class)
public class Bean {
}
''')

        ApplicationContext context = buildContextWithoutClass(sources, 'optional.MissingIndex')

        expect:
        context.getAllBeanDefinitions().any { it.beanType.name == 'optional.Bean' }
        BeanDefinitionReference<?> reference = context.getBeanDefinitionReferences().find { it.beanType.name == 'optional.Bean' }
        reference.indexes.length == 0

        cleanup:
        context?.close()
    }

    private ApplicationContext buildContextWithoutClass(JavaFiles sources, String missingClass) {
        try (JavaParser parser = newJavaParser()) {
            JavaFileObject[] sourceFiles = sources.files.stream()
                    .map { JavaFileObjects.forSourceString(it.key, it.value) }
                    .toArray(JavaFileObject[]::new)
            Iterable<? extends JavaFileObject> compiledFiles = parser.generate(sourceFiles)
            ClassLoader classLoader = new GeneratedClassLoader(compiledFiles, this.class.classLoader, missingClass)

            ApplicationContextBuilder builder = ApplicationContext.builder()
            builder.classLoader(classLoader)
            builder.environments('test')
            configureContext(builder)
            builder.beanDefinitionsProvider {
                def references = compiledFiles.findAll { JavaFileObject file ->
                    file.kind == JavaFileObject.Kind.CLASS &&
                            (file.name.endsWith(BeanDefinitionWriter.CLASS_SUFFIX + '$Reference.class') ||
                                    file.name.endsWith(BeanDefinitionWriter.CLASS_SUFFIX + '.class'))
                }.collect { JavaFileObject file ->
                    String name = file.toUri().toString().substring('mem:///CLASS_OUTPUT/'.length())
                            .replace('/', '.') - '.class'
                    (BeanDefinitionReference) classLoader.loadClass(name).getDeclaredConstructor().newInstance()
                }
                references + getBuiltInBeanReferences()
            }
            builder.build().start()
        }
    }

    private static final class GeneratedClassLoader extends ClassLoader {
        private final Map<String, JavaFileObject> generatedClasses = new HashMap<>()
        private final String missingClass

        GeneratedClassLoader(Iterable<? extends JavaFileObject> generatedFiles, ClassLoader parent, String missingClass) {
            super(parent)
            this.missingClass = missingClass
            generatedFiles.findAll { it.kind == JavaFileObject.Kind.CLASS }.each { JavaFileObject file ->
                String name = file.toUri().toString().substring('mem:///CLASS_OUTPUT/'.length())
                        .replace('/', '.') - '.class'
                generatedClasses.put(name, file)
            }
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name)
                if (loaded == null) {
                    if (name == missingClass) {
                        throw new ClassNotFoundException(name)
                    }
                    if (generatedClasses.containsKey(name)) {
                        loaded = findClass(name)
                    } else {
                        loaded = super.loadClass(name, false)
                    }
                }
                if (resolve && loaded.classLoader == this) {
                    resolveClass(loaded)
                }
                loaded
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            JavaFileObject generated = generatedClasses.get(name)
            if (generated == null) {
                throw new ClassNotFoundException(name)
            }
            try (InputStream input = generated.openInputStream()) {
                byte[] bytecode = input.readAllBytes()
                defineClass(name, bytecode, 0, bytecode.length)
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e)
            }
        }
    }
}

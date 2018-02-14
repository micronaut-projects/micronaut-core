/*
 * Copyright 2018 original authors
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
package org.particleframework.ast.groovy.utils;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.particleframework.core.annotation.Internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extended version of {@link GroovyClassLoader} that can be used to test dependency injection compilation
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class InMemoryByteCodeGroovyClassLoader extends GroovyClassLoader {

    private Map<String, byte[]> generatedClasses = new ConcurrentHashMap<>();
    private Map<String, Class> loadedClasses = new ConcurrentHashMap<>();

    public InMemoryByteCodeGroovyClassLoader() {
    }

    public InMemoryByteCodeGroovyClassLoader(ClassLoader loader) {
        super(loader);
    }

    public InMemoryByteCodeGroovyClassLoader(GroovyClassLoader parent) {
        super(parent);
    }

    public InMemoryByteCodeGroovyClassLoader(ClassLoader parent, CompilerConfiguration config, boolean useConfigurationClasspath) {
        super(parent, config, useConfigurationClasspath);
    }

    public InMemoryByteCodeGroovyClassLoader(ClassLoader loader, CompilerConfiguration config) {
        super(loader, config);
    }

    public void addClass(String name, byte[] code) {
        if(name != null && code != null) {
            generatedClasses.put(name, code);
        }
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        System.out.println("generatedClasses = " + generatedClasses.keySet());
        if(loadedClasses.containsKey(name)) {
            return loadedClasses.get(name);
        }
        else if(generatedClasses.containsKey(name)) {
            return loadedClasses.computeIfAbsent(name, className -> defineClass(className, generatedClasses.get(className)));
        }
        else {
            return super.loadClass(name);
        }
    }
}

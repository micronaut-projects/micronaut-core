
/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.io.support

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


/**
 * Base functionality for loading grails.factories
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class FactoriesLoaderSupport {
    /** The location to look for the factories. Can be present in multiple JAR files. */
    static final String FACTORIES_RESOURCE_LOCATION = "META-INF/grails.factories"

    private static ConcurrentMap<Integer, Map<String,String[]>> loadedPropertiesForClassLoader = new ConcurrentHashMap<Integer, Map<String,String[]>>()

    /**
     * Loads the names of the classes from grails.factories without loading the classes themselves
     *
     * @param factoryClass The factory class
     * @param classLoader The ClassLoader
     *
     * @return An array of classes that implement the factory class
     */
    static String[] loadFactoryNames(Class<?> factoryClass, ClassLoader classLoader = FactoriesLoaderSupport.class.classLoader) {
        String factoryClassName = factoryClass.getName()
        return loadFactoryNames(factoryClassName, classLoader)
    }

    /**
     * Loads the names of the classes from grails.factories without loading the classes themselves
     *
     * @param factoryClass The factory class
     * @param classLoader The ClassLoader
     *
     * @return An array of classes that implement the factory class
     */
    static String[] loadFactoryNames(String factoryClassName, ClassLoader classLoader = FactoriesLoaderSupport.class.classLoader) {
        try {
            Map<String, String[]> loadedProperties = loadedPropertiesForClassLoader.get(System.identityHashCode(classLoader))
            if (loadedProperties == null) {
                Set<String> allKeys = [] as Set
                def urls = classLoader.getResources(FACTORIES_RESOURCE_LOCATION);
                Collection<Properties> allProperties = []
                urls.each { URL url ->
                    def properties = new Properties()
                    url.withInputStream { InputStream input ->
                        properties.load(input)
                    }
                    allProperties.add properties
                    allKeys.addAll((Set<String>) properties.keySet())
                }
                Map<String, String[]> mergedFactoryNames = [:]
                for (String propertyName : allKeys) {
                    Set<String> result = [] as Set
                    for (Properties props : allProperties) {
                        String factoryClassNames = props.getProperty(propertyName)
                        if (factoryClassNames) {
                            result.addAll factoryClassNames.split(',').toList()
                        }
                    }
                    mergedFactoryNames.put propertyName, result as String[]
                }
                loadedProperties = loadedPropertiesForClassLoader.putIfAbsent(System.identityHashCode(classLoader), mergedFactoryNames)
                if (loadedProperties == null) {
                    loadedProperties = mergedFactoryNames
                }
            }
            return loadedProperties.get(factoryClassName)
        }
        catch (IOException ex) {
            throw new IllegalArgumentException("Unable to load [$factoryClassName] factories from location [$FACTORIES_RESOURCE_LOCATION]", ex);
        }
    }
}

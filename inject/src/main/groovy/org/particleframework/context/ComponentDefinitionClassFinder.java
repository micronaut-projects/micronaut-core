package org.particleframework.context;

import org.particleframework.inject.ComponentDefinitionClass;

import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * Loads the uninitialized component definitions
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ComponentDefinitionClassFinder implements Iterable<ComponentDefinitionClass> {

    private final ClassLoader classLoader;

    public ComponentDefinitionClassFinder(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ComponentDefinitionClassFinder() {
        this.classLoader = getClass().getClassLoader();
    }

    @Override
    public Iterator<ComponentDefinitionClass> iterator() {
        return new LazyIterator(classLoader);
    }

    private class LazyIterator implements Iterator<ComponentDefinitionClass> {
        private final ClassLoader classLoader;
        Enumeration<URL> descriptors = null;

        LazyIterator(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public boolean hasNext() {
            if(descriptors == null) {

            }
            return false;
        }

        @Override
        public ComponentDefinitionClass next() {
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Cannot mutate component definition finder");
        }
    }
}

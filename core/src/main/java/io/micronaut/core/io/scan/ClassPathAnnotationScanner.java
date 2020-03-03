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
package io.micronaut.core.io.scan;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * <p>An optimized classpath scanner that includes the ability to optionally scan JAR files.</p>
 * <p>The implementation avoids loading the classes themselves by parsing the class definitions and reading
 * only the annotations.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class ClassPathAnnotationScanner implements AnnotationScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ClassPathAnnotationScanner.class);

    private final ClassLoader classLoader;
    private boolean includeJars;

    /**
     * @param classLoader The class loader
     */
    public ClassPathAnnotationScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.includeJars = true;
    }

    /**
     * Default constructor.
     */
    public ClassPathAnnotationScanner() {
        this(ClassPathAnnotationScanner.class.getClassLoader());
    }

    /**
     * Whether to include JAR files.
     *
     * @param includeJars The jar files to include
     * @return This scanner
     */
    protected ClassPathAnnotationScanner includeJars(boolean includeJars) {
        this.includeJars = includeJars;
        return this;
    }

    /**
     * Scan the given packages.
     *
     * @param annotation The annotation to scan for
     * @param pkg        The package to scan
     * @return A stream of classes
     */
    @Override
    public Stream<Class> scan(String annotation, String pkg) {
        if (pkg == null) {
            return Stream.empty();
        }
        List<Class> classes = doScan(annotation, pkg);
        return classes.stream();
    }

    /**
     * @param annotation The annotation
     * @param pkg        The package
     * @return The list of class
     */
    protected List<Class> doScan(String annotation, String pkg) {
        try {
            String packagePath = pkg.replace('.', '/').concat("/");
            List<Class> classes = new ArrayList<>();
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            if (!resources.hasMoreElements() && LOG.isDebugEnabled()) {
                LOG.debug("No resources found under package path: {}", packagePath);
            }
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    try {
                        File file = new File(url.toURI());
                        traverseFile(annotation, classes, file);
                    } catch (URISyntaxException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Ignoring file [" + url + "] due to URI error: " + e.getMessage(), e);
                        }
                    }
                } else if (includeJars && Arrays.asList("jar", "zip", "war").contains(protocol)) {
                    URLConnection con = url.openConnection();
                    if (con instanceof JarURLConnection) {
                        JarURLConnection jarCon = (JarURLConnection) con;
                        JarFile jarFile = jarCon.getJarFile();
                        jarFile.stream()
                            .filter(entry -> {
                                String name = entry.getName();
                                return name.startsWith(packagePath) && name.endsWith(ClassUtils.CLASS_EXTENSION) && name.indexOf('$') == -1;
                            })
                            .forEach(entry -> {
                                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                                    scanInputStream(annotation, inputStream, classes);
                                } catch (IOException e) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Ignoring JAR entry [" + entry.getName() + "] due to I/O error: " + e.getMessage(), e);
                                    }
                                } catch (ClassNotFoundException e) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Ignoring JAR entry [" + entry.getName() + "]. Class not found: " + e.getMessage(), e);
                                    }
                                }
                            });
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Ignoring JAR URI entry [" + url + "]. No JarURLConnection found.");
                        }
                        // TODO: future support for servlet containers
                    }

                }
            }
            return classes;
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Ignoring I/O Exception scanning package: " + pkg, e);
            }
            return Collections.emptyList();
        }
    }

    /**
     * @param annotation The annotation
     * @param classes    The classes
     * @param file       The file
     */
    protected void traverseFile(String annotation, List<Class> classes, File file) {
        if (file.isDirectory()) {
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(file.toPath())) {
                dirs.forEach(path -> {
                    File f = path.toFile();
                    if (f.isDirectory()) {
                        traverseFile(annotation, classes, f);
                    } else {
                        scanFile(annotation, f, classes);
                    }
                });
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Ignoring directory [" + file + "] due to I/O error: " + e.getMessage(), e);
                }
            }
        } else {
            scanFile(annotation, file, classes);
        }
    }

    /**
     * @param annotation The annotation
     * @param file       The file
     * @param classes    The classes
     */
    protected void scanFile(String annotation, File file, List<Class> classes) {
        String fileName = file.getName();
        if (fileName.endsWith(".class")) {
            // ignore generated classes
            if (fileName.indexOf('$') == -1) {
                try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                    scanInputStream(annotation, inputStream, classes);
                } catch (IOException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Ignoring file [" + file.getName() + "] due to I/O error: " + e.getMessage(), e);
                    }
                } catch (ClassNotFoundException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Ignoring file [" + file.getName() + "]. Class not found: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    private void scanInputStream(String annotation, InputStream inputStream, List<Class> classes) throws IOException, ClassNotFoundException {
        AnnotationClassReader annotationClassReader = new AnnotationClassReader(inputStream);
        AnnotatedTypeInfoVisitor classVisitor = new AnnotatedTypeInfoVisitor();
        annotationClassReader.accept(classVisitor, AnnotationClassReader.SKIP_DEBUG);
        if (classVisitor.hasAnnotation(annotation)) {
            classes.add(classLoader.loadClass(classVisitor.getTypeName()));
        }
    }
}

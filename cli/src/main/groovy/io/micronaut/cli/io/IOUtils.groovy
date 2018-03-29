/*
 * Copyright 2002-2013 the original author or authors.
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
package io.micronaut.cli.io

import io.micronaut.cli.util.CliSettings
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.io.support.SpringIOUtils
import io.micronaut.cli.io.support.UrlResource

import java.nio.file.Paths

/**
 * Utility methods for performing I/O operations.
 *
 * @author Graeme Rocher
 * @since 2.4
 */
@CompileStatic
class IOUtils extends SpringIOUtils {
    public static final String RESOURCE_JAR_PREFIX = ".jar!"
    public static final String RESOURCE_WAR_PREFIX = ".war!"

    private static String applicationDirectory

    /**
     * Gracefully opens a stream for a file, throwing exceptions where appropriate. Based off the commons-io method
     *
     * @param file The file
     * @return The stream
     */
    static BufferedInputStream openStream(File file)  {
        if(!file.exists()) {
            throw new FileNotFoundException("File $file does not exist")
        }
        else {
            if ( file.directory ) {
                throw new IOException("File $file exists but is a directory")
            }
            else if ( !file.canRead() ) {
                throw new IOException("File $file cannot be read")
            }
            else {
                file.newInputStream()
            }
        }
    }

    /**
     * Convert a reader to a String, reading the data from the reader
     * @param reader The reader
     * @return The string
     */
    static String toString(Reader reader) {
        def writer = new StringWriter()
        copy reader, writer
        writer.toString()
    }

    /**
     * Convert a stream to a String, reading the data from the stream
     * @param stream The stream
     * @return The string
     */
    static String toString(InputStream stream, String encoding = null) {
        def writer = new StringWriter()
        copy stream, writer, encoding
        writer.toString()
    }

    /**
     * Copy an InputStream to the given writer with the given encoding
     * @param input The input
     * @param output The writer
     * @param encoding The encoding
     */
    static void copy(InputStream input, Writer output, String encoding = null) {
        def reader = encoding ? new InputStreamReader(input, encoding) : new InputStreamReader(input)
        copy(reader, output)
    }

    /**
     * Finds a JAR file for the given class
     * @param targetClass The target class
     * @return The JAR file
     */
    static File findJarFile(Class targetClass) {
        def resource = findClassResource(targetClass)
        findJarFile(resource)
    }

    /**
     * Whether the given URL is within a binary like a JAR or WAR file
     * @param url The URL
     * @return True if it is
     */
    static boolean isWithinBinary(URL url) {
        String protocol = url.protocol
        return protocol == null || protocol != 'file'
    }

    /**
     * Finds a JAR for the given resource
     *
     * @param resource The resource
     * @return The JAR file or null if it can't be found
     */
    static File findJarFile(Resource resource) {
        def absolutePath = resource?.getFilename()
        if (absolutePath) {
            final jarPath = absolutePath.substring("file:".length(), absolutePath.lastIndexOf("!"))
            new File(jarPath)
        }
        return null
    }
    /**
     * Finds a JAR for the given resource
     *
     * @param resource The resource
     * @return The JAR file or null if it can't be found
     */
    static File findJarFile(URL resource) {
        if(resource?.protocol == 'jar') {
            def absolutePath = resource?.path
            if (absolutePath) {
                try {
                    return Paths.get(new URL(absolutePath.substring(0, absolutePath.lastIndexOf("!"))).toURI()).toFile()
                } catch (MalformedURLException e) {
                    return null
                }
            }
        }
        return null
    }

    /**
     * Returns the URL resource for the location on disk of the given class or null if it cannot be found
     *
     * @param targetClass The target class
     * @return The URL to class file or null
     */
    static URL findClassResource(Class targetClass) {
        targetClass.getResource('/' + targetClass.name.replace(".", "/") + ".class")
    }

    /**
     * Returns a URL that represents the root classpath resource where the given class was loaded from
     *
     * @param targetClass The target class
     * @return The URL to class file or null
     */
    static URL findRootResource(Class targetClass) {
        def pathToClassFile = '/' + targetClass.name.replace(".", "/") + ".class"
        def classRes = targetClass.getResource(pathToClassFile)
        if(classRes) {
            def rootPath = classRes.toString() - pathToClassFile
            return new URL("$rootPath/")
        }
        throw new IllegalStateException("Root classpath resource not found! Check your disk permissions")
    }


    /**
     * This method differs from {@link #findRootResource(java.lang.Class)} in that it will find the root URL where to load resources defined in src/main/resources
     *
     * At development time this with be build/main/resources, but in production it will be relative to the class.
     *
     * @param targetClass
     * @param path
     * @return
     */
    static URL findRootResourcesURL(Class targetClass) {
        def pathToClassFile = '/' + targetClass.name.replace(".", "/") + ".class"
        def classRes = targetClass.getResource(pathToClassFile)
        if(classRes) {
            String rootPath = classRes.toString() - pathToClassFile
            if(rootPath.endsWith(CliSettings.BUILD_CLASSES_PATH)) {
                rootPath = rootPath.replace('/build/classes/', '/build/resources/')
            }
            else {
                rootPath = "$rootPath/"
            }
            return new URL(rootPath)
        }
        return null
    }

    /**
     * Returns the URL resource for the location on disk of the given class or null if it cannot be found
     *
     * @param targetClass The target class
     * @return The URL to class file or null
     */
    static URL findJarResource(Class targetClass) {
        def classUrl = findClassResource(targetClass)
        if(classUrl != null) {
            def urlPath = classUrl.toString()
            def bang = urlPath.lastIndexOf("!")

            if(bang > -1) {
                def newPath = urlPath.substring(0, bang)
                return new URL("${newPath}!/")
            }
        }
        return null
    }
    /**
     * Finds a URL within a JAR relative (from the root) to the given class
     *
     * @param targetClass
     * @param path
     * @return
     */
    static URL findResourceRelativeToClass(Class targetClass, String path) {
        def pathToClassFile = '/' + targetClass.name.replace(".", "/") + ".class"
        def classRes = targetClass.getResource(pathToClassFile)
        if(classRes) {
            def rootPath = classRes.toString() - pathToClassFile
            if(rootPath.endsWith(CliSettings.BUILD_CLASSES_PATH)) {
                rootPath = rootPath.replace('/build/classes/', '/build/resources/')
            }
            return new URL("$rootPath$path")
        }
        return null
    }


    @Memoized
    static File findApplicationDirectoryFile() {
        def directory = findApplicationDirectory()
        if(directory) {
            def f = new File(directory)
            if(f.exists()) {

                return f
            }
        }
        return null
    }

    /**
     * Finds the application directory for the given class
     *
     * @param targetClass The target class
     * @return The application directory or null if it can't be found
     */
    static File findApplicationDirectoryFile(Class targetClass) {

        def rootResource = findRootResource(targetClass)
        if(rootResource != null) {

            try {
                def rootFile = new UrlResource(rootResource).file.canonicalFile
                def rootPath = rootFile.path
                def buildClassespath = CliSettings.BUILD_CLASSES_PATH.replace('/', File.separator)
                if(rootPath.contains(buildClassespath)) {
                    return new File(rootPath - buildClassespath)
                } else {
                    File appDir = findGrailsApp(rootFile)
                    if (appDir != null) {
                        return appDir
                    }
                }
            } catch (FileNotFoundException fnfe) {
                return null
            }
        }
        return null
    }

    /**
     * Finds a source file for the given class name
     *
     * @param className The class name
     * @return The source file
     */
    @Memoized
    static File findSourceFile(String className) {
        File applicationDir = CliSettings.BASE_DIR
        File file = null
        if(applicationDir != null) {
            String fileName = className.replace('.' as char, File.separatorChar) + '.groovy'
            List<File> allFiles = [ new File(applicationDir, "src/main/groovy") ]
            File[] files = new File(applicationDir, "grails-app").listFiles(new FileFilter() {
                @Override
                boolean accept(File f) {
                    return f.isDirectory() && !f.isHidden() && !f.name.startsWith('.')
                }
            })
            if(files != null) {
                allFiles.addAll( Arrays.asList(files) )
            }
            for(File dir in allFiles) {
                File possibleFile = new File(dir, fileName)
                if(possibleFile.exists()) {
                    file = possibleFile
                    break
                }
            }

        }
        return file
    }

    /**
     * Finds the directory where the Application class is contained
     * @return The application directory
     */
    @Memoized
    static String findApplicationDirectory() {
        if(applicationDirectory) {
            return applicationDirectory
        }

        String location = null
        try {
            String mainClassName = System.getProperty(CliSettings.MAIN_CLASS_NAME)
            if(!mainClassName) {
                def stackTraceElements = Arrays.asList( Thread.currentThread().getStackTrace() ).reverse()
                if(stackTraceElements) {
                    for(lastElement in stackTraceElements) {

                        def className = lastElement.className
                        def methodName = lastElement.methodName
                        if(className.endsWith(".Application") && methodName == '<clinit>') {
                            mainClassName = className
                            break
                        }
                    }
                }
            }
            if(mainClassName) {


                final Class<?> mainClass = Thread.currentThread().contextClassLoader.loadClass(mainClassName)
                final URL classResource = mainClass ? findClassResource(mainClass) : null
                if(classResource) {
                    File file = new UrlResource(classResource).getFile()
                    String path = file.canonicalPath


                    String buildClassesPath = CliSettings.BUILD_CLASSES_PATH.replace('/', File.separator)
                    if(path.contains(buildClassesPath)) {
                        location = path.substring(0, path.indexOf(buildClassesPath) - 1)
                    } else {
                        File appDir = findGrailsApp(file)
                        if (appDir != null) {
                            location = appDir.canonicalPath
                        }
                    }
                }
            }

        } catch (ClassNotFoundException e) {
            // ignore
        } catch (IOException e) {
            // ignore
        }
        applicationDirectory = location
        return location
    }

    private static File findGrailsApp(File file) {
        File parent = file.parentFile
        while (parent != null) {
            File grailsApp = new File(parent, "grails-app")
            if (grailsApp.isDirectory()) {
                return parent
            } else {
                parent = parent.parentFile
            }
        }
        return null
    }
}

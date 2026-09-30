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
package io.micronaut.core.io.service;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A precomputed index of the services of a closed class path, which lets service loading on the JVM skip the scan of
 * {@code META-INF/services} and {@code META-INF/micronaut} for the types it covers.
 *
 * <p>Besides the class loader it was built for, the index only holds names. Services are still instantiated, filtered
 * and ordered as the scan does it, with one fork-join task per name, joined in order. A packager that sees the
 * complete class path of an application builds the index at packaging time, for example with
 * {@link ServiceIndexBuilder}, and registers it at run time through a {@link StaticOptimizations.Loader}:</p>
 *
 * <pre>{@code
 * public final class GeneratedServiceIndexLoader implements StaticOptimizations.Loader<ServiceIndex> {
 *     public ServiceIndex load() {
 *         return ServiceIndex.ofTrusted(GeneratedServiceIndexLoader.class.getClassLoader(), micronautServices, standardServices, classPath);
 *     }
 * }
 * }</pre>
 *
 * <p>The index follows these rules:</p>
 * <ul>
 *     <li>It is only served for the {@link #classLoader() class loader} it was built for. Any other class loader,
 *     including a child of that class loader, scans the class path.</li>
 *     <li>It is ignored in native image code, where the service table built by the native image feature is used. An
 *     index that a loader registers there, which includes the build of the image, is dropped and never read. A
 *     producer must therefore not emit an index for a native packaging: the generated loader and its names would
 *     only add to the image.</li>
 *     <li>A {@link SoftServiceLoader.StaticServiceLoader} registered for a type through
 *     {@link SoftServiceLoader.Optimizations} is still used for that type.</li>
 *     <li>The name condition given to {@link SoftServiceLoader#load(Class, ClassLoader, java.util.function.Predicate)}
 *     is tested on every name of the index.</li>
 *     <li>At most one index can be registered: registering a second one fails.</li>
 *     <li>Setting the system property {@value #ENABLED_PROPERTY} to {@code false} switches the index off, so that
 *     the class path is scanned. The property is read at the start of each lookup: when a
 *     {@link SoftServiceLoader.ServiceCollector} is created, which {@link SoftServiceLoader} does for each collection
 *     of a type, and on each call of
 *     {@link MicronautMetaServiceLoaderUtils#findMicronautMetaServiceEntries(ClassLoader, String)}. A collector that
 *     is reused keeps the answer it was created with.</li>
 *     <li>The first lookup that uses the index logs that at {@code INFO}, once.</li>
 * </ul>
 *
 * <p>An index describes a closed world: a JAR added to the class path after the index was built is not seen for the
 * types the index covers, and nothing fails: its services, bean definitions included, are just not found. It should
 * only be emitted for packagings whose class path cannot change. Two checks guard against an index that does not
 * match the class path it is served for:</p>
 * <ul>
 *     <li>If the index lists the {@link #classPath() class path} it was built for, the first lookup compares it with
 *     the class path of the class loader: the entries must have the same file names and, where the index gives a
 *     size, the same sizes, in any order. If they differ, a {@code WARN} names the entries that differ and the class
 *     path is scanned, as without an index. The class path of the class loader is known for the system class loader,
 *     from the {@code java.class.path} system property, and for a {@link URLClassLoader} whose URLs are all files,
 *     from those URLs. When exactly one of those entries is a file, as for an application started with
 *     {@code java -jar}, the entries that the {@code Class-Path} attribute of the manifest of that JAR names are part
 *     of the class path that is compared, so a thin JAR is compared together with the libraries its manifest lists.
 *     For any other class loader nothing is compared, and the producer is responsible for only registering an index
 *     that matches. An index without a class path is not checked and costs no I/O.</li>
 *     <li>If the system property {@value #VALIDATE_PROPERTY} is {@code true} when the index is first used, that
 *     lookup also scans the class path and compares the result with the index, without regard to the order. If the
 *     names differ, or the class path check above fails, that lookup and every later one fail with a
 *     {@link ServiceConfigurationError} that lists the differences. This costs more than not having an index, so it
 *     is meant for the tests of a producer and for diagnosing an application, not for production.</li>
 * </ul>
 *
 * <p>The comparison of the class path is cheap, and it does not see everything. The {@code INFO} line says what was
 * compared.</p>
 * <ul>
 *     <li>A {@code Class-Path} attribute is only followed for a class path that has a single JAR, and only for that
 *     JAR. A class path of several JARs is compared without the entries that the manifests of those JARs name,
 *     because reading them would open every JAR when the application starts, which costs more than the index saves.
 *     The JARs that a {@code Class-Path} names are not asked for a {@code Class-Path} of their own. An index served
 *     for such a class path can therefore miss the services of a JAR that only a manifest leads to.</li>
 *     <li>Entries are compared by their file names, not by their paths, which differ between the machine that builds
 *     and the one that runs. A JAR that is renamed between packaging and deployment, for example when it is copied
 *     into a container image under another name, no longer matches its entry: the index is then set aside, with the
 *     {@code WARN}, on every start. A producer must list the names the files have when the application runs, and
 *     register the index without a class path when it cannot know them.</li>
 *     <li>The class path of a parent class loader is not compared. The index lists the services that its class
 *     loader finds, which include those of its parents, but only the URLs of a {@link URLClassLoader} itself are
 *     compared. An index that was built for such a class loader is therefore still served after a JAR is added to
 *     the class path of its parent, and the services of that JAR are missed. The {@code INFO} line says so when
 *     the class loader has a parent other than the platform class loader. The parent of the system class loader
 *     is the platform class loader, which has no class path.</li>
 *     <li>The content of a directory is not compared, nor is a file that is replaced by another one of the same name
 *     and size, and a JAR that an agent appends to the class path is not seen.</li>
 * </ul>
 *
 * <p>The registered index is held for the life of the JVM, and so is its class loader, which is therefore never
 * unloaded. That is harmless for a packaged application, whose index is built for the class loader of the
 * application, but it rules out registering an index for a unit that a container deploys and undeploys.</p>
 *
 * @param classLoader       The class loader the index was built for, and the only one it is served for
 * @param micronautServices The entries under {@code META-INF/micronaut/<type>/} for every type, as
 *                          {@link MicronautMetaServiceLoaderUtils#findAllMicronautMetaServices(ClassLoader)} finds them.
 *                          It is exhaustive: a type that is missing has no such entries
 * @param standardServices  The names listed by the {@code META-INF/services/<type>} files of each indexed type, in the
 *                          order the scan finds them. A type that is missing is scanned
 * @param classPath         The entries of the class path the index was built for, in any order, or null to not
 *                          compare the class path
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
@Experimental
public record ServiceIndex(ClassLoader classLoader,
                           Map<String, Set<String>> micronautServices,
                           Map<String, List<String>> standardServices,
                           @Nullable List<ClassPathEntry> classPath)
    implements StaticOptimizations.SetOnce, StaticOptimizations.JvmOnly {

    /**
     * The system property that switches the index off when it is set to {@code false}.
     */
    public static final String ENABLED_PROPERTY = "micronaut.service.index.enabled";

    /**
     * The system property that, when it is set to {@code true}, makes the first lookup that uses the index compare
     * it with a scan of the class path, and fail if they differ.
     */
    public static final String VALIDATE_PROPERTY = "micronaut.service.index.validate";

    private static final int MAX_REPORTED_NAMES = 20;

    private static final Object CHECK_LOCK = new Object();

    // the check of the index that was last looked up: at most one index is registered, so one slot is enough
    @Nullable
    private static volatile Check lastCheck;

    /**
     * Creates an index with copies of the given collections, which keep their order and cannot be modified.
     *
     * @param classLoader       The class loader the index was built for, and the only one it is served for
     * @param micronautServices The entries under {@code META-INF/micronaut/<type>/} for every type
     * @param standardServices  The names listed by the {@code META-INF/services/<type>} files of each indexed type
     * @param classPath         The entries of the class path the index was built for, or null to not compare the
     *                          class path
     */
    public ServiceIndex {
        Objects.requireNonNull(classLoader, "classLoader");
        if (!(micronautServices instanceof TrustedMap<?>)) {
            micronautServices = copyOfSets(micronautServices);
        }
        if (!(standardServices instanceof TrustedMap<?>)) {
            standardServices = copyOfLists(standardServices);
        }
        classPath = classPath == null ? null : List.copyOf(classPath);
    }

    /**
     * Creates an index that does not list the class path it was built for, with copies of the given collections.
     *
     * @param classLoader       The class loader the index was built for, and the only one it is served for
     * @param micronautServices The entries under {@code META-INF/micronaut/<type>/} for every type
     * @param standardServices  The names listed by the {@code META-INF/services/<type>} files of each indexed type
     */
    public ServiceIndex(ClassLoader classLoader, Map<String, Set<String>> micronautServices, Map<String, List<String>> standardServices) {
        this(classLoader, micronautServices, standardServices, null);
    }

    /**
     * Creates an index that uses the given maps, with their sets and lists, as they are, where the constructors copy
     * every one of them. It is meant for generated code, which builds the collections for the index alone and runs
     * at the start of the application, where the copies would be made for nothing.
     *
     * <p>The caller must not modify the maps, the sets or the lists afterwards, and the order in which they iterate
     * is the order the services are loaded in. Service loading only iterates the sets and asks for their size, so a
     * set does not have to be hashed: an unmodifiable {@link Set} view of an array of names is enough.</p>
     *
     * @param classLoader       The class loader the index was built for, and the only one it is served for
     * @param micronautServices The entries under {@code META-INF/micronaut/<type>/} for every type
     * @param standardServices  The names listed by the {@code META-INF/services/<type>} files of each indexed type
     * @param classPath         The entries of the class path the index was built for, or null to not compare the
     *                          class path
     * @return The index
     */
    public static ServiceIndex ofTrusted(ClassLoader classLoader,
                                         Map<String, Set<String>> micronautServices,
                                         Map<String, List<String>> standardServices,
                                         @Nullable List<ClassPathEntry> classPath) {
        return new ServiceIndex(classLoader, new TrustedMap<>(micronautServices), new TrustedMap<>(standardServices), classPath);
    }

    /**
     * Returns this index if it applies to a lookup. The first lookup that the index applies to checks it, as the
     * class documents, and logs whether it is used.
     *
     * @param lookupClassLoader The class loader of the lookup
     * @return This index, or null if the class path must be scanned
     * @throws ServiceConfigurationError If the index was validated and does not match the class path
     */
    @Nullable ServiceIndex forLookup(ClassLoader lookupClassLoader) {
        if (classLoader != lookupClassLoader
            || NativeImageUtils.inImageCode()
            || StringUtils.FALSE.equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY))) {
            return null;
        }
        Check check = lastCheck;
        if (check == null || check.index != this) {
            check = check();
        }
        if (check.failure != null) {
            throw new ServiceConfigurationError(check.failure);
        }
        return check.usable ? this : null;
    }

    /**
     * Lists the class path of a class loader, for the class loaders whose class path is known: the system class
     * loader, whose class path is the {@code java.class.path} system property, and a {@link URLClassLoader} whose
     * URLs are all files. An entry that does not exist is left out, as the class loader ignores it. When exactly
     * one entry is a file, the entries that the {@code Class-Path} attribute of its manifest names are listed too.
     * The class path of the parents of the class loader is not listed: see {@link ServiceIndex}.
     *
     * @param classLoader The class loader
     * @return The entries of the class path, in its order, or null if the class path is not known
     */
    static @Nullable List<ClassPathEntry> classPathOf(ClassLoader classLoader) {
        return classPathOf(classLoader, new StringBuilder());
    }

    /**
     * Lists the class path of a class loader, and says where its entries come from.
     *
     * @param classLoader The class loader
     * @param compared    Where to say, for the log, where the entries come from
     * @return The entries of the class path, in its order, or null if the class path is not known
     */
    private static @Nullable List<ClassPathEntry> classPathOf(ClassLoader classLoader, StringBuilder compared) {
        List<File> files = new ArrayList<>();
        String source;
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            try {
                for (URL url : urlClassLoader.getURLs()) {
                    if (!"file".equals(url.getProtocol())) {
                        return null;
                    }
                    files.add(new File(url.toURI()));
                }
            } catch (URISyntaxException | IllegalArgumentException e) {
                return null;
            }
            source = "the URLs of the class loader";
        } else if (classLoader == ClassLoader.getSystemClassLoader()) {
            for (String element : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
                // an empty element is the working directory
                files.add(new File(element.isEmpty() ? "." : element));
            }
            source = "java.class.path";
        } else {
            return null;
        }
        List<ClassPathEntry> entries = new ArrayList<>(files.size());
        File jar = null;
        int jars = 0;
        for (File file : files) {
            // java.io.File and not java.nio.file.Files: in a JVM that has just started, File reads a class path of
            // 55 entries in 0.2 ms, where Files takes 0.6 ms
            if (file.isFile()) {
                entries.add(new ClassPathEntry(file.getName(), file.length()));
                jar = file;
                jars++;
            } else if (file.isDirectory()) {
                entries.add(new ClassPathEntry(file.getName(), -1));
            }
        }
        // a StringBuilder and not a concatenation, which would link a call site on the startup path
        appendCount(compared, entries.size()).append(source);
        if (jar != null && jars == 1) {
            // the class path of an application started with java -jar: the JAR, and what its manifest names
            int named = addManifestClassPath(jar, files, entries, !(classLoader instanceof URLClassLoader));
            if (named == 0) {
                compared.append(", to which the manifest of ").append(jar.getName()).append(" adds nothing");
            } else {
                appendCount(compared.append(" and "), named).append("the Class-Path of the manifest of ").append(jar.getName());
            }
        } else if (jars > 1) {
            // reading the manifest of every JAR would open every JAR, which costs more than the index saves
            compared.append(", without the Class-Path of the manifests of its JARs");
        }
        if (classLoader instanceof URLClassLoader) {
            ClassLoader parent = classLoader.getParent();
            if (parent != null && parent != ClassLoader.getPlatformClassLoader()) {
                // the services of the parents are indexed, and their class path is not listed
                compared.append("; the class path of its parent class loaders is not compared");
            }
        }
        return entries;
    }

    private static StringBuilder appendCount(StringBuilder builder, int entries) {
        return builder.append("the ").append(entries).append(entries == 1 ? " entry of " : " entries of ");
    }

    /**
     * Adds the entries that the {@code Class-Path} attribute of the manifest of a JAR names, the way the class loader
     * finds them: each name is a URL relative to the JAR, a URL that ends with a slash is a directory, and any other
     * one is a JAR. A name that is not the URL of a file, that does not exist, or that is already on the class path
     * is left out. The manifests of the JARs that are added are not read.
     *
     * @param jar       The JAR
     * @param classPath The files of the class path the JAR is on
     * @param entries   The entries of that class path, to add to
     * @param canonical Whether the names are relative to the canonical path of the JAR, as they are for the system
     *                  class loader, which follows a link to the JAR, or to its path as it is given
     * @return The number of entries that were added
     */
    private static int addManifestClassPath(File jar, List<File> classPath, List<ClassPathEntry> entries, boolean canonical) {
        String names = null;
        // without the verification of a signed JAR: only the names are read
        try (JarFile jarFile = new JarFile(jar, false)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                names = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            }
        } catch (IOException e) {
            // not a JAR that can be read: the class loader finds nothing in it either
        }
        if (names == null) {
            return 0;
        }
        File base = jar.getAbsoluteFile();
        if (canonical) {
            try {
                base = jar.getCanonicalFile();
            } catch (IOException e) {
                // the absolute path is the closest there is
            }
        }
        Set<String> seen = new HashSet<>();
        for (File file : classPath) {
            seen.add(file == jar ? base.getPath() : file.getAbsolutePath());
        }
        int added = 0;
        StringTokenizer tokenizer = new StringTokenizer(names);
        while (tokenizer.hasMoreTokens()) {
            ClassPathEntry entry = manifestClassPathEntry(base, tokenizer.nextToken(), seen);
            if (entry != null) {
                entries.add(entry);
                added++;
            }
        }
        return added;
    }

    /**
     * Finds the entry that a name of a {@code Class-Path} adds to the class path.
     *
     * @param jar  The JAR whose manifest has the name, which the name is relative to
     * @param name The name
     * @param seen The paths of the entries that are on the class path already, to add to
     * @return The entry, or null if the name adds none
     */
    private static @Nullable ClassPathEntry manifestClassPathEntry(File jar, String name, Set<String> seen) {
        File file;
        boolean directory;
        String entryName;
        if (isPlainPath(name)) {
            // the usual name, such as lib/some.jar: it is resolved as a path, because parsing 55 names as URLs takes
            // 1.2 ms in a JVM that has just started
            file = name.charAt(0) == '/' ? new File(name) : new File(jar.getParentFile(), name);
            directory = name.endsWith("/");
            entryName = file.getName();
        } else {
            try {
                URI uri = jar.toURI().resolve(name);
                file = new File(uri);
                directory = uri.getPath().endsWith("/");
            } catch (IllegalArgumentException e) {
                // not the URL of a file
                return null;
            }
            // "." is listed under that name, as it is when java.class.path names it, and not under the name of the
            // directory of the JAR, which depends on where the application is deployed
            String written = new File(name).getName();
            entryName = ".".equals(written) || "..".equals(written) ? written : file.getName();
        }
        if (!seen.add(file.getPath())) {
            return null;
        }
        if (directory) {
            return file.isDirectory() ? new ClassPathEntry(entryName, -1) : null;
        }
        return file.isFile() ? new ClassPathEntry(entryName, file.length()) : null;
    }

    /**
     * Whether a name of a {@code Class-Path} is a path that is the same once it is resolved as a URL: it has no
     * scheme, no host, no escaped character, no query, no fragment, and no {@code .} or {@code ..} segment.
     *
     * @param name The name
     * @return True if the name can be resolved as a path
     */
    private static boolean isPlainPath(String name) {
        return name.indexOf(':') < 0 && name.indexOf('%') < 0 && name.indexOf('?') < 0 && name.indexOf('#') < 0
            && !name.startsWith("//") && !name.contains("./") && !name.endsWith(".");
    }

    private Check check() {
        Check check;
        synchronized (CHECK_LOCK) {
            check = lastCheck;
            if (check != null && check.index == this) {
                return check;
            }
            check = newCheck();
            lastCheck = check;
        }
        // Logged outside the lock, and only by the lookup that checked the index. The logger is not a static field
        // because the class can be initialized while a native image is built
        Logger log = LoggerFactory.getLogger(ServiceIndex.class);
        if (check.usable) {
            if (log.isInfoEnabled()) {
                int entries = 0;
                for (Set<String> names : micronautServices.values()) {
                    entries += names.size();
                }
                log.info("Using the service index registered for class loader {}: META-INF/micronaut ({} entries of {} service types) and the META-INF/services files of {} service types are not scanned ({}). Set the system property {} to false to scan the class path instead.",
                    classLoader, entries, micronautServices.size(), standardServices.size(), check.detail, ENABLED_PROPERTY);
            }
        } else if (check.failure == null) {
            log.warn("Not using the service index registered for class loader {}, and scanning the class path instead, because {}", classLoader, check.detail);
        }
        return check;
    }

    private Check newCheck() {
        boolean validate = Boolean.getBoolean(VALIDATE_PROPERTY);
        String checked = "class path not compared: the index does not list one";
        if (classPath != null) {
            StringBuilder compared = new StringBuilder();
            List<ClassPathEntry> actual = classPathOf(classLoader, compared);
            if (actual == null) {
                checked = "class path not compared: the class loader is neither the system class loader nor a URLClassLoader of files";
            } else {
                String difference = classPathDifference(classPath, actual);
                if (difference != null) {
                    String reason = "the index was built for a different class path: " + difference + " (compared with " + compared + ")";
                    return new Check(this, false, reason, validate ? failure(reason) : null);
                }
                checked = compared.insert(0, "class path compared: ").toString();
            }
        }
        if (validate) {
            String differences = differencesFromScan();
            if (differences != null) {
                String reason = "the index differs from a scan of the class path:" + differences;
                return new Check(this, false, reason, failure(reason));
            }
            checked += ", names validated against a scan";
        }
        return new Check(this, true, checked, null);
    }

    private String failure(String reason) {
        return "The service index registered for class loader " + classLoader + " failed its validation: " + reason;
    }

    /**
     * Compares two class paths without regard to their order.
     *
     * @param expected The class path the index was built for
     * @param actual   The class path of the class loader
     * @return A description of the entries that differ, or null if there is none
     */
    // S3824: computeIfAbsent would add a lambda to link on the startup path the index exists to shorten
    @SuppressWarnings("java:S3824")
    private static @Nullable String classPathDifference(List<ClassPathEntry> expected, List<ClassPathEntry> actual) {
        if (sameInOrder(expected, actual)) {
            return null;
        }
        Map<String, List<ClassPathEntry>> unmatched = new HashMap<>();
        for (ClassPathEntry entry : actual) {
            List<ClassPathEntry> entries = unmatched.get(entry.name());
            if (entries == null) {
                entries = new ArrayList<>(1);
                unmatched.put(entry.name(), entries);
            }
            entries.add(entry);
        }
        List<ClassPathEntry> missing = new ArrayList<>();
        // the entries with a size are matched first, so that an entry without a size does not take their match
        matchEntries(expected, true, unmatched, missing);
        matchEntries(expected, false, unmatched, missing);
        List<ClassPathEntry> added = new ArrayList<>();
        for (ClassPathEntry entry : actual) {
            List<ClassPathEntry> entries = unmatched.get(entry.name());
            if (entries != null && entries.remove(entry)) {
                added.add(entry);
            }
        }
        if (missing.isEmpty() && added.isEmpty()) {
            return null;
        }
        StringBuilder difference = new StringBuilder("the class path ");
        if (!missing.isEmpty()) {
            difference.append("lacks ");
            appendNames(difference, missing);
            difference.append(", which the index was built for");
        }
        if (!added.isEmpty()) {
            difference.append(missing.isEmpty() ? "has " : ", and has ");
            appendNames(difference, added);
            difference.append(missing.isEmpty() ? ", which the index was not built for" : ", which it was not built for");
        }
        return difference.toString();
    }

    private static boolean sameInOrder(List<ClassPathEntry> expected, List<ClassPathEntry> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            ClassPathEntry entry = expected.get(i);
            if (!entry.name().equals(actual.get(i).name()) || (entry.size() >= 0 && entry.size() != actual.get(i).size())) {
                return false;
            }
        }
        return true;
    }

    private static void matchEntries(List<ClassPathEntry> expected, boolean withSize, Map<String, List<ClassPathEntry>> unmatched, List<ClassPathEntry> missing) {
        for (ClassPathEntry entry : expected) {
            if ((entry.size() >= 0) == withSize && !removeMatch(entry, unmatched.get(entry.name()))) {
                missing.add(entry);
            }
        }
    }

    private static boolean removeMatch(ClassPathEntry expected, @Nullable List<ClassPathEntry> candidates) {
        if (candidates != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (expected.size() < 0 || expected.size() == candidates.get(i).size()) {
                    candidates.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Scans the class path and compares the names it finds with the names of the index, without regard to their
     * order.
     *
     * @return A description of the names that differ, or null if there is none
     */
    private @Nullable String differencesFromScan() {
        StringBuilder differences = new StringBuilder();
        try {
            Map<String, Set<String>> scanned = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader);
            Set<String> types = new TreeSet<>(scanned.keySet());
            types.addAll(micronautServices.keySet());
            for (String type : types) {
                appendDifference(differences, "META-INF/micronaut/" + type, scanned.getOrDefault(type, Set.of()), micronautServices.getOrDefault(type, Set.of()));
            }
            for (Map.Entry<String, List<String>> indexed : standardServices.entrySet()) {
                String type = indexed.getKey();
                appendDifference(differences, SoftServiceLoader.META_INF_SERVICES + '/' + type, ServiceScanner.readStandardServiceNames(classLoader, type), indexed.getValue());
            }
        } catch (IOException e) {
            differences.append("\n  the class path cannot be scanned: ").append(e);
        }
        return differences.isEmpty() ? null : differences.toString();
    }

    private static void appendDifference(StringBuilder differences, String location, Collection<String> scanned, Collection<String> indexed) {
        // a name that two files list is loaded twice, so the names are counted
        Map<String, Integer> counts = new TreeMap<>();
        for (String name : scanned) {
            counts.merge(name, 1, Integer::sum);
        }
        for (String name : indexed) {
            counts.merge(name, -1, Integer::sum);
        }
        List<String> missing = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            if (count.getValue() > 0) {
                missing.add(count.getKey());
            } else if (count.getValue() < 0) {
                stale.add(count.getKey());
            }
        }
        if (missing.isEmpty() && stale.isEmpty()) {
            return;
        }
        differences.append("\n  ").append(location).append(':');
        if (!missing.isEmpty()) {
            differences.append(" missing from the index ");
            appendNames(differences, missing);
        }
        if (!stale.isEmpty()) {
            differences.append(missing.isEmpty() ? " not on the class path " : ", not on the class path ");
            appendNames(differences, stale);
        }
    }

    private static void appendNames(StringBuilder builder, List<?> names) {
        int reported = Math.min(names.size(), MAX_REPORTED_NAMES);
        builder.append(names.subList(0, reported));
        if (names.size() > reported) {
            builder.append(" and ").append(names.size() - reported).append(" more");
        }
    }

    private static Map<String, Set<String>> copyOfSets(Map<String, Set<String>> services) {
        Map<String, Set<String>> copy = new LinkedHashMap<>(services.size());
        for (Map.Entry<String, Set<String>> entry : services.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, List<String>> copyOfLists(Map<String, List<String>> services) {
        Map<String, List<String>> copy = new LinkedHashMap<>(services.size());
        for (Map.Entry<String, List<String>> entry : services.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * An entry of the class path an index was built for.
     *
     * @param name The file name of the entry, without the directories that lead to it, which differ between the
     *             machine that builds the index and the one that runs the application. It must be the name the entry
     *             has when the application runs: an index that lists a JAR under the name it had when it was
     *             packaged is set aside when that JAR is deployed under another name. A producer that cannot know
     *             the names registers the index without a class path
     * @param size The size of the entry in bytes if it is a file, or a negative number to not compare the size:
     *             for a directory, and for a file whose size the producer cannot know, such as the JAR that holds
     *             the index itself
     * @since 5.3.0
     */
    @Experimental
    public record ClassPathEntry(String name, long size) {

        /**
         * @param name The file name of the entry
         * @param size The size of the entry in bytes, or a negative number to not compare the size
         */
        public ClassPathEntry {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String toString() {
            return size < 0 ? name : name + " (" + size + " bytes)";
        }
    }

    /**
     * The check of an index against the class path it is served for.
     *
     * @param index   The index
     * @param usable  Whether the index is served
     * @param detail  What was checked, or why the index is not served
     * @param failure The message of the error that every lookup fails with, or null if the lookups do not fail
     */
    private record Check(ServiceIndex index, boolean usable, String detail, @Nullable String failure) {
    }

    /**
     * An unmodifiable view of a map that {@link #ofTrusted} was given, which the constructor recognizes and does not
     * copy. Only that method creates one.
     *
     * @param <V> The type of the values
     */
    private static final class TrustedMap<V> extends AbstractMap<String, V> {

        private final Map<String, V> map;

        TrustedMap(Map<String, V> map) {
            this.map = Collections.unmodifiableMap(map);
        }

        @Override
        public Set<Entry<String, V>> entrySet() {
            return map.entrySet();
        }

        @Override
        public @Nullable V get(Object key) {
            return map.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return map.containsKey(key);
        }
    }
}

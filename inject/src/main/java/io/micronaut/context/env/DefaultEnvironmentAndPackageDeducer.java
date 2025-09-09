/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.env;

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.micronaut.context.env.Environment.AMAZON_EC2;
import static io.micronaut.context.env.Environment.ANDROID;
import static io.micronaut.context.env.Environment.AZURE;
import static io.micronaut.context.env.Environment.CLOUD_PLATFORM_PROPERTY;
import static io.micronaut.context.env.Environment.DIGITAL_OCEAN;
import static io.micronaut.context.env.Environment.ENVIRONMENTS_ENV;
import static io.micronaut.context.env.Environment.ENVIRONMENTS_PROPERTY;
import static io.micronaut.context.env.Environment.GOOGLE_COMPUTE;
import static io.micronaut.context.env.Environment.IBM;
import static io.micronaut.context.env.Environment.ORACLE_CLOUD;
import static io.micronaut.context.env.Environment.TEST;

/**
 * The DefaultEnvironmentAndPackageDeducer class provides an implementation for deducing
 * environment names and package names based on runtime and contextual information.
 * It serves as the primary implementation of the {@link EnvironmentNamesDeducer}
 * and {@link EnvironmentPackagesDeducer} interfaces.
 *
 * This class analyzes factors such as the runtime environment, cloud provider, system properties,
 * and stack traces to determine the set of associated environment names and packages. It supports
 * both Linux and Windows platforms and includes mechanism to detect various cloud environments
 * (e.g., AWS, Google Cloud, Oracle Cloud, etc.).
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class DefaultEnvironmentAndPackageDeducer implements EnvironmentNamesDeducer, EnvironmentPackagesDeducer {

    private static final Boolean DEDUCE_ENVIRONMENT_DEFAULT = true;
    private static final String EC2_LINUX_HYPERVISOR_FILE = "/sys/hypervisor/uuid";
    private static final String EC2_LINUX_BIOS_VENDOR_FILE = "/sys/devices/virtual/dmi/id/bios_vendor";
    private static final String EC2_WINDOWS_HYPERVISOR_CMD = "wmic path win32_computersystemproduct get uuid";
    private static final String AWS_LAMBDA_FUNCTION_NAME_ENV = "AWS_LAMBDA_FUNCTION_NAME";
    private static final String K8S_ENV = "KUBERNETES_SERVICE_HOST";
    private static final String PCF_ENV = "VCAP_SERVICES";
    private static final String HEROKU_DYNO = "DYNO";
    private static final String GOOGLE_APPENGINE_ENVIRONMENT = "GAE_ENV";
    // CHECKSTYLE:OFF
    private static final String GOOGLE_COMPUTE_METADATA = "metadata.google.internal";
    // CHECKSTYLE:ON
    private static final String ORACLE_CLOUD_ASSET_TAG_FILE = "/sys/devices/virtual/dmi/id/chassis_asset_tag";
    private static final String ORACLE_CLOUD_WINDOWS_ASSET_TAG_CMD = "wmic systemenclosure get smbiosassettag";
    private static final String DO_SYS_VENDOR_FILE = "/sys/devices/virtual/dmi/id/sys_vendor";

    private final Logger log;
    private final ApplicationContextConfiguration configuration;
    private final Boolean deduceEnvironments;

    private LinkedHashSet<String> environments;
    private String packageName;

    public DefaultEnvironmentAndPackageDeducer(@NonNull Logger log, @NonNull ApplicationContextConfiguration configuration) {
        this.log = log;
        this.configuration = configuration;
        this.deduceEnvironments = configuration.getDeduceEnvironments().orElse(null);
    }

    @Override
    public Set<String> deduceEnvironmentNames() {
        deduce();
        return environments;
    }

    @Override
    public List<String> deducePackages() {
        deduce();
        return packageName == null ? List.of() : List.of(packageName);
    }

    private synchronized void deduce() {
        if (environments == null) {
            List<String> envEnvironments = new ArrayList<>();
            for (String values : Arrays.asList(CachedEnvironment.getProperty(ENVIRONMENTS_PROPERTY), CachedEnvironment.getenv(ENVIRONMENTS_ENV))) {
                if (StringUtils.isNotEmpty(values)) {
                    for (String string : StringUtils.splitOmitEmptyStrings(values, ',')) {
                        envEnvironments.add(string.trim());
                    }
                }
            }

            List<String> configurationEnvironments = configuration.getEnvironments();
            List<String> specifiedNames = CollectionUtils.concat(envEnvironments, configurationEnvironments);
            environments = new LinkedHashSet<>(specifiedNames);

            deduceEnvironmentsAndPackage();

            if (environments.isEmpty() && specifiedNames.isEmpty()) {
                specifiedNames = configuration.getDefaultEnvironments();
            }
            specifiedNames.forEach(this.environments::remove);
            this.environments.addAll(specifiedNames);
        }
    }

    private void deduceEnvironmentsAndPackage() {
        if (environments.contains(Environment.FUNCTION)) {
            performFunctionDeduction(environments);
        } else {
            final boolean deduceEnvironments = shouldDeduceEnvironments();
            if (configuration.isDeducePackage() || deduceEnvironments) {
                performStackTraceInspection(configuration.isDeducePackage(), deduceEnvironments, environments);
            }
            boolean deduceComputePlatform = shouldDeduceCloudEnvironment() && !environments.contains(ANDROID);
            performEnvironmentDeduction(deduceComputePlatform, environments);
        }
    }

    /**
     * @return Whether environment names and packages should be deduced
     */
    private boolean shouldDeduceEnvironments() {
        if (deduceEnvironments != null) {
            log.debug("Environment deduction was set explicitly via builder to: {}", deduceEnvironments);
            return deduceEnvironments;
        }
        if (configuration.isEnableDefaultPropertySources()) {
            String deduceProperty = CachedEnvironment.getProperty(Environment.DEDUCE_ENVIRONMENT_PROPERTY);
            String deduceEnv = CachedEnvironment.getenv(Environment.DEDUCE_ENVIRONMENT_ENV);

            if (StringUtils.isNotEmpty(deduceEnv)) {
                boolean deduce = Boolean.parseBoolean(deduceEnv);
                log.debug("Environment deduction was set via environment variable to: {}", deduce);
                return deduce;
            } else if (StringUtils.isNotEmpty(deduceProperty)) {
                boolean deduce = Boolean.parseBoolean(deduceProperty);
                log.debug("Environment deduction was set via system property to: {}", deduce);
                return deduce;
            }
            boolean deduceDefault = DEDUCE_ENVIRONMENT_DEFAULT;
            log.debug("Environment deduction is using the default of: {}", deduceDefault);
            return deduceDefault;
        }
        return false;
    }

    /**
     * @return Whether cloud environment should be deduced based on environment variable, system property or configuration
     */
    private boolean shouldDeduceCloudEnvironment() {
        String deduceEnv = CachedEnvironment.getenv(Environment.DEDUCE_CLOUD_ENVIRONMENT_ENV);
        if (StringUtils.isNotEmpty(deduceEnv)) {
            boolean deduce = Boolean.parseBoolean(deduceEnv);
            log.debug("Cloud environment deduction was set via environment variable to: {}", deduce);
            return deduce;
        }
        String deduceProperty = CachedEnvironment.getProperty(Environment.DEDUCE_CLOUD_ENVIRONMENT_PROPERTY);
        if (StringUtils.isNotEmpty(deduceProperty)) {
            boolean deduce = Boolean.parseBoolean(deduceProperty);
            log.debug("Cloud environment deduction was set via system property to: {}", deduce);
            return deduce;
        }
        return configuration.isDeduceCloudEnvironment();
    }

    private static void performFunctionDeduction(Set<String> environments) {
        // deduce AWS Lambda
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(AWS_LAMBDA_FUNCTION_NAME_ENV))) {
            environments.add(AMAZON_EC2);
            environments.add(Environment.CLOUD);
        }
    }

    private static void performEnvironmentDeduction(boolean deduceComputePlatform, Set<String> environments) {
        // deduce k8s
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(K8S_ENV))) {
            environments.add(Environment.KUBERNETES);
            environments.add(Environment.CLOUD);
        }
        // deduce CF
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(PCF_ENV))) {
            environments.add(Environment.CLOUD_FOUNDRY);
            environments.add(Environment.CLOUD);
        }

        // deduce heroku
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(HEROKU_DYNO))) {
            environments.add(Environment.HEROKU);
            environments.add(Environment.CLOUD);
            deduceComputePlatform = false;
        }

        // deduce GAE
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(GOOGLE_APPENGINE_ENVIRONMENT))) {
            environments.add(Environment.GAE);
            environments.add(GOOGLE_COMPUTE);
            environments.add(Environment.CLOUD);
            deduceComputePlatform = false;
        }

        if (deduceComputePlatform) {
            performComputePlatformDeduction(environments);
        }
    }

    private static void performComputePlatformDeduction(Set<String> environments) {
        ComputePlatform computePlatform = determineCloudProvider();
        if (computePlatform != null) {
            switch (computePlatform) {
                case GOOGLE_COMPUTE:
                    //instantiate bean for GC metadata discovery
                    environments.add(GOOGLE_COMPUTE);
                    environments.add(Environment.CLOUD);
                    break;
                case AMAZON_EC2:
                    //instantiate bean for ec2 metadata discovery
                    environments.add(AMAZON_EC2);
                    environments.add(Environment.CLOUD);
                    break;
                case ORACLE_CLOUD:
                    environments.add(ORACLE_CLOUD);
                    environments.add(Environment.CLOUD);
                    break;
                case AZURE:
                    // not yet implemented
                    environments.add(AZURE);
                    environments.add(Environment.CLOUD);
                    break;
                case IBM:
                    // not yet implemented
                    environments.add(IBM);
                    environments.add(Environment.CLOUD);
                    break;
                case DIGITAL_OCEAN:
                    environments.add(DIGITAL_OCEAN);
                    environments.add(Environment.CLOUD);
                    break;
                case OTHER:
                    // do nothing here
                    break;
                default:
                    // no-op
            }
        }
    }

    private void performStackTraceInspection(boolean deducePackage, boolean deduceEnvironments, Set<String> environments) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        int len = stackTrace.length;
        for (int i = 0; i < len; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            String className = stackTraceElement.getClassName();

            analyzeStackTraceElement(deducePackage, deduceEnvironments, environments, stackTrace, len, i, stackTraceElement, className);
        }
    }

    private void analyzeStackTraceElement(boolean deducePackage, boolean deduceEnvironments, Set<String> environments, StackTraceElement[] stackTrace, int len, int i, StackTraceElement stackTraceElement, String className) {
        if (deducePackage) {
            if (className.startsWith("io.micronaut")) {
                int nextIndex = i + 1;
                if (nextIndex < len) {
                    StackTraceElement next = stackTrace[nextIndex];
                    if (!next.getClassName().startsWith("io.micronaut")) {
                        packageName = NameUtils.getPackageName(next.getClassName());
                    }
                }
            }

            if (stackTraceElement.getMethodName().contains("$spock_")) {
                packageName = NameUtils.getPackageName(className);
            }
        }
        if (deduceEnvironments) {
            if (Stream.of("org.spockframework", "org.junit", "io.kotlintest", "io.kotest").anyMatch(className::startsWith)) {
                environments.add(TEST);
            }

            if (className.startsWith("com.android")) {
                environments.add(ANDROID);
            }
        }
    }

    private static ComputePlatform determineCloudProvider() {
        String computePlatform = CachedEnvironment.getProperty(CLOUD_PLATFORM_PROPERTY);
        if (computePlatform != null) {

            try {
                return ComputePlatform.valueOf(computePlatform);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Illegal value specified for [" + CLOUD_PLATFORM_PROPERTY + "]: " + computePlatform);
            }

        }
        boolean isWindows = CachedEnvironment.getProperty("os.name")
            .toLowerCase().startsWith("windows");

        if (isWindows ? isEC2Windows() : isEC2Linux()) {
            return ComputePlatform.AMAZON_EC2;
        }

        if (isGoogleCompute()) {
            return ComputePlatform.GOOGLE_COMPUTE;
        }

        if (isWindows ? isOracleCloudWindows() : isOracleCloudLinux()) {
            return ComputePlatform.ORACLE_CLOUD;
        }

        if (isDigitalOcean()) {
            return ComputePlatform.DIGITAL_OCEAN;
        }

        //TODO check for azure and IBM
        //Azure - see https://blog.mszcool.com/index.php/2015/04/detecting-if-a-virtual-machine-runs-in-microsoft-azure-linux-windows-to-protect-your-software-when-distributed-via-the-azure-marketplace/
        //IBM - uses cloudfoundry, will have to use that to probe
        // if all else fails not a cloud server that we can tell
        return ComputePlatform.BARE_METAL;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isGoogleCompute() {
        try {
            InetAddress.getByName(GOOGLE_COMPUTE_METADATA);
            return true;
        } catch (Exception e) {
            // well not google then
        }
        return false;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isOracleCloudLinux() {
        return readFile(ORACLE_CLOUD_ASSET_TAG_FILE).toLowerCase().contains("oraclecloud");
    }

    private static Optional<Process> runWindowsCmd(String cmd) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("cmd.exe", "/c", cmd);
            builder.redirectErrorStream(true);
            builder.directory(new File(CachedEnvironment.getProperty("user.home")));
            Process process = builder.start();
            return Optional.of(process);
        } catch (IOException ignore) {

        }
        return Optional.empty();
    }

    private static StringBuilder readProcessStream(Process process) {
        StringBuilder stdout = new StringBuilder();

        try {
            //Read out dir output
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                stdout.append(line);
            }
        } catch (IOException e) {
            // ignore
        }

        return stdout;
    }

    private static boolean isOracleCloudWindows() {
        Optional<Process> optionalProcess = runWindowsCmd(ORACLE_CLOUD_WINDOWS_ASSET_TAG_CMD);
        if (!optionalProcess.isPresent()) {
            return false;
        }
        Process process = optionalProcess.get();
        StringBuilder stdout = readProcessStream(process);

        //Wait to get exit value
        try {
            int exitValue = process.waitFor();
            if (exitValue == 0 && stdout.toString().toLowerCase().contains("oraclecloud")) {
                return true;
            }
        } catch (InterruptedException e) {
            // test negative
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static boolean isEC2Linux() {
        if (readFile(EC2_LINUX_HYPERVISOR_FILE).startsWith("ec2")) {
            return true;
        } else if (readFile(EC2_LINUX_BIOS_VENDOR_FILE).toLowerCase().startsWith("amazon ec2")) {
            return true;
        }

        return false;
    }

    private static String readFile(String path) {
        try {
            Path pathPath = Paths.get(path);
            if (!Files.exists(pathPath)) {
                return "";
            }
            return new String(Files.readAllBytes(pathPath)).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean isEC2Windows() {
        Optional<Process> optionalProcess = runWindowsCmd(EC2_WINDOWS_HYPERVISOR_CMD);
        if (!optionalProcess.isPresent()) {
            return false;
        }
        Process process = optionalProcess.get();
        StringBuilder stdout = readProcessStream(process);
        //Wait to get exit value
        try {
            int exitValue = process.waitFor();
            if (exitValue == 0 && stdout.toString().startsWith("EC2")) {
                return true;
            }
        } catch (InterruptedException e) {
            // test negative
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static boolean isDigitalOcean() {
        return "digitalocean".equalsIgnoreCase(readFile(DO_SYS_VENDOR_FILE));
    }

}

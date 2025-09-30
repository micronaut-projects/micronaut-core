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
package io.micronaut.http.ssl;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Certificate provider that loads certificate material from files on disk and can
 * refresh the material when the underlying files change using a file watcher or scheduler.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@EachBean(FileCertificateProvider.Config.class)
@BootstrapContextCompatible
public final class FileCertificateProvider implements CertificateProvider {
    private static final Logger LOG = LoggerFactory.getLogger(FileCertificateProvider.class);

    private final String name;
    private final Flux<KeyStore> flux;
    private final WatchService watchService;

    /**
     * Create a provider that loads and optionally refreshes certificate material from disk.
     *
     * @param config file configuration
     * @param scheduler scheduled executor for periodic refresh
     * @param blockingExecutor executor used for blocking file watching
     * @throws Exception if the initial load fails or watcher setup fails
     */
    FileCertificateProvider(
        @NonNull Config config,
        @NonNull @jakarta.inject.Named(TaskExecutors.SCHEDULED) ExecutorService scheduler,
        @NonNull @jakarta.inject.Named(TaskExecutors.BLOCKING) Executor blockingExecutor
    ) throws Exception {
        if (config.refreshMode == RefreshMode.NONE) {
            flux = Flux.just(load(config));
            watchService = null;
        } else {
            Sinks.Many<KeyStore> sink = Sinks.many().replay().latest();
            flux = sink.asFlux();

            WatchService ws = null;
            if (config.refreshMode == RefreshMode.FILE_WATCHER || config.refreshMode == RefreshMode.FILE_WATCHER_OR_SCHEDULER) {
                Path directory = config.path.getParent();
                try {
                    ws = directory.getFileSystem().newWatchService();
                    directory.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                } catch (UnsupportedOperationException uoe) {
                    if (ws != null) {
                        try {
                            ws.close();
                        } catch (IOException ioe) {
                            uoe.addSuppressed(ioe);
                        }
                        ws = null;
                    }
                    if (config.refreshMode == RefreshMode.FILE_WATCHER) {
                        throw uoe;
                    } else {
                        LOG.debug("Failed to create watch service, falling back on scheduled refresh", uoe);
                    }
                }
            }
            this.watchService = ws;

            sink.tryEmitNext(load(config)).orThrow();

            if (ws != null) {
                WatchService finalWs = ws;
                blockingExecutor.execute(() -> {
                    while (true) {
                        try {
                            WatchKey key = finalWs.take();
                            boolean changed = false;
                            for (WatchEvent<?> event : key.pollEvents()) {
                                if (event.context() instanceof Path ctx && (ctx.getFileName().equals(config.path.getFileName()) || (config.certificatePath != null && ctx.getFileName().equals(config.certificatePath.getFileName())))) {
                                    changed = true;
                                    break;
                                }
                            }
                            key.reset();
                            if (changed) {
                                loadSafe(sink, config);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (ClosedWatchServiceException e) {
                            break;
                        }
                    }
                });
            } else {
                ((ScheduledExecutorService) scheduler).scheduleWithFixedDelay(
                    () -> loadSafe(sink, config),
                    config.refreshInterval.toNanos(),
                    config.refreshInterval.toNanos(),
                    TimeUnit.NANOSECONDS);
            }
        }

        name = config.name;
    }

    /**
     * Stop watching files and release resources.
     *
     * @throws IOException if closing the watch service fails
     */
    @PreDestroy
    void close() throws IOException {
        watchService.close();
    }

    private static void loadSafe(Sinks.Many<KeyStore> sink, Config config) {
        try {
            sink.tryEmitNext(load(config)).orThrow();
        } catch (Exception e) {
            LOG.error("Failed to load certificate file", e);
        }
    }

    private static @NonNull KeyStore load(Config config) throws GeneralSecurityException, PemParser.NotPemException, IOException {
        byte[] mainBytes = Files.readAllBytes(config.path);
        byte[] certBytes;
        if (config.certificatePath != null) {
            if (config.format != Format.PEM) {
                throw new ConfigurationException("A separate certificate-path is only permitted for PEM format. Please mark this certificate as PEM format explicitly.");
            }
            certBytes = Files.readAllBytes(config.certificatePath);
        } else {
            certBytes = null;
        }

        return load(config, mainBytes, certBytes);
    }

    static @NonNull KeyStore load(AbstractCertificateFileConfig config, byte[] mainBytes, byte[] certBytes) throws GeneralSecurityException, PemParser.NotPemException, IOException {
        KeyStore ks;
        if (config.format == null) {
            try {
                ks = load(config, mainBytes, certBytes, Format.JKS);
            } catch (IOException e) {
                if (e.getCause() instanceof UnrecoverableKeyException) {
                    throw e;
                }
                try {
                    ks = load(config, mainBytes, certBytes, Format.PEM);
                } catch (PemParser.NotPemException f) {
                    // probably should have been loaded as KS
                    e.addSuppressed(new Exception("Also tried and failed to load the input as PEM", f));
                    throw e;
                } catch (Exception f) {
                    // probably should have been loaded as PEM
                    f.addSuppressed(new Exception("Also tried and failed to load the input as a key store", e));
                    throw f;
                }
            }
        } else {
            ks = load(config, mainBytes, certBytes, config.format);
        }
        return ks;
    }

    private static KeyStore load(AbstractCertificateFileConfig config, byte @NonNull [] mainBytes, byte @Nullable [] certBytes, @NonNull Format format) throws GeneralSecurityException, IOException, PemParser.NotPemException {
        KeyStore ks = KeyStore.getInstance(format == Format.JKS ? "JKS" : "PKCS12");
        if (format == Format.PEM) {
            ks.load(null, null);
            PemParser pemParser = new PemParser(null, config.password);
            List<Object> mainObjects = pemParser.loadPem(mainBytes);
            if (mainObjects.get(0) instanceof PrivateKey pk) {
                List<Object> certObjects;
                if (mainObjects.size() > 1) {
                    certObjects = mainObjects.subList(1, mainObjects.size());
                    if (certBytes != null) {
                        throw new ConfigurationException("Separate cert-path given but main file also contained certificates");
                    }
                } else if (certBytes != null) {
                    certObjects = pemParser.loadPem(certBytes);
                } else {
                    certObjects = List.of();
                }
                ks.setKeyEntry("key", pk, null, SslBuilder.certificates(certObjects).toArray(new X509Certificate[0]));
            } else {
                if (certBytes != null) {
                    throw new ConfigurationException("Separate cert-path given but main file only contained certificates");
                }
                List<X509Certificate> certificates = SslBuilder.certificates(mainObjects);
                for (int i = 0; i < certificates.size(); i++) {
                    ks.setCertificateEntry("cert" + i, certificates.get(i));
                }
            }
        } else {
            ks.load(new ByteArrayInputStream(mainBytes), config.password == null ? null : config.password.toCharArray());
        }
        return ks;
    }

    @NonNull
    @Override
    public Publisher<@NonNull KeyStore> getKeyStore() {
        return flux;
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    /**
     * Configuration for file-based certificate material. Supports JKS/PKCS12 and PEM,
     * with optional automatic reloading.
     */
    @EachProperty(CONFIG_PREFIX + ".file")
    @BootstrapContextCompatible
    public static final class Config extends AbstractCertificateFileConfig {
        @NonNull
        private Path path;
        @Nullable
        private Path certificatePath;
        @NonNull
        private RefreshMode refreshMode = RefreshMode.FILE_WATCHER_OR_SCHEDULER;
        @NonNull
        private Duration refreshInterval = Duration.ofHours(1);

        public Config(@Parameter @NonNull String name) {
            super(name);
        }

        /**
         * Path to the main certificate file to load. For JKS/PKCS12 this is a key store (optionally protected by {@code password}).
         * For PEM this file may contain a private key and optionally certificates; when it only contains a private key, the certificate
         * chain can be supplied via {@code certificatePath}. Reload behavior is controlled by {@code refreshMode}/{@code refreshInterval}.
         * @return the path to the certificate file
         */
        public @NonNull Path getPath() {
            return path;
        }

        /**
         * Path to the main certificate file to load. For JKS/PKCS12 this is a key store (optionally protected by {@code password}).
         * For PEM this file may contain a private key and optionally certificates; when it only contains a private key, the certificate
         * chain can be supplied via {@code certificatePath}. Reload behavior is controlled by {@code refreshMode}/{@code refreshInterval}.
         * @param path the path to the certificate file
         */
        public void setPath(@NonNull Path path) {
            this.path = path;
        }

        /**
         * Optional path to a separate PEM-encoded certificate chain. Only supported when the {@code format} is {@code PEM}.
         * When set, the main file at {@code path} must contain only the private key.
         * @return the path to the PEM certificate chain or {@code null}
         */
        public @Nullable Path getCertificatePath() {
            return certificatePath;
        }

        /**
         * Optional path to a separate PEM-encoded certificate chain. Only supported when the {@code format} is {@code PEM}.
         * When set, the main file at {@code path} must contain only the private key.
         * @param certificatePath the path to the PEM certificate chain or {@code null}
         */
        public void setCertificatePath(@Nullable Path certificatePath) {
            this.certificatePath = certificatePath;
        }

        /**
         * Strategy for reloading the certificate file. {@code NONE}: load once. {@code FILE_WATCHER}: watch the directory and reload on changes.
         * {@code SCHEDULER}: periodically reload. {@code FILE_WATCHER_OR_SCHEDULER}: use a watcher when supported, otherwise fall back to scheduled reloads.
         * @return the refresh strategy
         */
        public @NonNull RefreshMode getRefreshMode() {
            return refreshMode;
        }

        /**
         * Strategy for reloading the certificate file. {@code NONE}: load once. {@code FILE_WATCHER}: watch the directory and reload on changes.
         * {@code SCHEDULER}: periodically reload. {@code FILE_WATCHER_OR_SCHEDULER}: use a watcher when supported, otherwise fall back to scheduled reloads.
         * @param refreshMode the refresh strategy
         */
        public void setRefreshMode(@NonNull RefreshMode refreshMode) {
            this.refreshMode = refreshMode;
        }

        /**
         * Interval used for scheduled reloads when the refresh mode uses a scheduler or when file watching is not available and a scheduled
         * fallback is used.
         * @return the refresh interval
         */
        public @NonNull Duration getRefreshInterval() {
            return refreshInterval;
        }

        /**
         * Interval used for scheduled reloads when the refresh mode uses a scheduler or when file watching is not available and a scheduled
         * fallback is used.
         * @param refreshInterval the refresh interval
         */
        public void setRefreshInterval(@NonNull Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }
    }

    /**
     * Strategy for reloading certificate files.
     */
    public enum RefreshMode {
        NONE,
        FILE_WATCHER,
        SCHEDULER,
        FILE_WATCHER_OR_SCHEDULER,
    }

    /**
     * Supported on-disk formats for certificate material.
     */
    public enum Format {
        JKS,
        PKCS12,
        PEM,
    }
}

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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Named;
import io.micronaut.scheduling.TaskExecutors;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@EachBean(SelfSignedCertificateProvider.Config.class)
@Requires(classes = X509Bundle.class)
@BootstrapContextCompatible
public class SelfSignedCertificateProvider implements CertificateProvider {
    private static final Logger LOG = LoggerFactory.getLogger(SelfSignedCertificateProvider.class);

    private final String name;
    private final Flux<KeyStore> bundleFlux;

    SelfSignedCertificateProvider(@NonNull Config config, @NonNull @jakarta.inject.Named(TaskExecutors.SCHEDULED) ExecutorService scheduler) throws Exception {
        name = config.name;
        Sinks.Many<KeyStore> sink = Sinks.many().replay().latest();
        update(config, sink);
        ((ScheduledExecutorService) scheduler).scheduleAtFixedRate(
            () -> {
                try {
                    update(config, sink);
                } catch (Exception e) {
                    LOG.warn("Failed to build self-signed certificate '{}'", config.name, e);
                }
            },
            config.updateInterval.toNanos(),
            config.updateInterval.toNanos(),
            TimeUnit.NANOSECONDS);
        bundleFlux = sink.asFlux();
    }

    private static void update(@NonNull Config config, Sinks.Many<KeyStore> sink) throws Exception {
        X509Bundle bundle = new CertificateBuilder()
            .algorithm(config.algorithm)
            .subject(config.subject)
            .notAfter(Instant.now().plus(config.lifetime))
            .setIsCertificateAuthority(true)
            .buildSelfSigned();
        sink.tryEmitNext(bundle.toKeyStore(null));
    }

    @Override
    public Publisher<KeyStore> getKeyStore() {
        return bundleFlux;
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    @EachProperty(CONFIG_PREFIX + ".self-signed")
    @BootstrapContextCompatible
    public static class Config implements Named {
        private final String name;

        private CertificateBuilder.Algorithm algorithm = CertificateBuilder.Algorithm.rsa4096;
        private String subject = "CN=localhost";
        private Duration updateInterval = Duration.ofDays(1);
        private Duration lifetime = Duration.ofDays(7);

        public Config(@Parameter String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getName() {
            return name;
        }

        public CertificateBuilder.Algorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(CertificateBuilder.Algorithm algorithm) {
            this.algorithm = algorithm;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public Duration getUpdateInterval() {
            return updateInterval;
        }

        public void setUpdateInterval(Duration updateInterval) {
            this.updateInterval = updateInterval;
        }

        public Duration getLifetime() {
            return lifetime;
        }

        public void setLifetime(Duration lifetime) {
            this.lifetime = lifetime;
        }
    }
}

package io.micronaut.discovery.vault.config;

import io.micronaut.http.annotation.Header;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;

public interface VaultConfigHttpClient<T extends AbstractVaultResponse> {

    String getDescription();

    Publisher<T> readConfigurationValues(@Nonnull @Header("X-Vault-Token") String token,
                                         @Nonnull String backend,
                                         @Nonnull String vaultKey);

}

package io.micronaut.discovery.oraclecloud

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.oraclecloud.vault.config.OracleCloudVaultClientConfiguration
import spock.lang.Specification

class VaultConfigurationTest extends Specification {

    void testConfig() {
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.config-client.enabled': true,
                'oraclecloud.vault.config.enabled': true,
                'oraclecloud.vault.vaults': [
                        ['ocid': 'ocid1.vault.oc1.phx....',
                         'compartment-ocid': 'ocid1.compartment.oc1....']
                ]]);
        OracleCloudVaultClientConfiguration config = ctx.getBean(OracleCloudVaultClientConfiguration.class)

        expect:
        1 == config.getVaults().size()
        "ocid1.vault.oc1.phx...." == config.getVaults().get(0).getOcid()
        "ocid1.compartment.oc1...." == config.getVaults().get(0).getCompartmentOcid()

        cleanup:
        ctx.close()
    }
}



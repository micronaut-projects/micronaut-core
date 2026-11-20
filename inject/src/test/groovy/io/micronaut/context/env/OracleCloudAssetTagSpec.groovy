package io.micronaut.context.env

import spock.lang.Specification

class OracleCloudAssetTagSpec extends Specification {

    void "the asset tag #assetTag is Oracle Cloud: #expected"() {
        expect:
        DefaultEnvironmentAndPackageDeducer.isOracleCloudAssetTag(assetTag) == expected

        where:
        assetTag            | expected
        "OracleCloud.com"   | true
        "ORACLECLOUD.COM\n" | true
        "Amazon EC2"        | false
        ""                  | false
    }

    void "the asset tag command with exit value #exitValue and output #stdout is Oracle Cloud: #expected"() {
        expect:
        DefaultEnvironmentAndPackageDeducer.isOracleCloudAssetTagOutput(exitValue, new StringBuilder(stdout)) == expected

        where:
        exitValue | stdout                      | expected
        0         | "ChassisAssetTag OracleCloud.com" | true
        0         | "ChassisAssetTag Default"   | false
        1         | "OracleCloud.com"           | false
    }
}

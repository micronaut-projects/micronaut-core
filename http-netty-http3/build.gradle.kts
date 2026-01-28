plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(libs.managed.netty.codec.http3) {
        exclude(group = "io.netty", module = "netty-codec-native-quic")
    }
    api(libs.managed.netty.codec.classes.quic)
    runtimeOnly(variantOf(libs.managed.netty.codec.native.quic) { classifier("linux-x86_64") })
    runtimeOnly(variantOf(libs.managed.netty.codec.native.quic) { classifier("linux-aarch_64") })
    runtimeOnly(variantOf(libs.managed.netty.codec.native.quic) { classifier("osx-x86_64") })
    runtimeOnly(variantOf(libs.managed.netty.codec.native.quic) { classifier("osx-aarch_64") })
    runtimeOnly(variantOf(libs.managed.netty.codec.native.quic) { classifier("windows-x86_64") })
}

micronautBuild {
    binaryCompatibility {
        // remove after 4.9.0
        enabled = false
    }
}

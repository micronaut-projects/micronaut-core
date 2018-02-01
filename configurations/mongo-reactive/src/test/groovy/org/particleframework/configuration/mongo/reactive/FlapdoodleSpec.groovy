package org.particleframework.configuration.mongo.reactive

import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import spock.lang.Specification

abstract class FlapdoodleSpec extends Specification {

    private static final String bindIp = "localhost"
    List<MongodProcess> processes = []

    void startServers(Integer... ports) {
        for (Integer port: ports) {
            IMongodConfig mongodConfig = new MongodConfigBuilder()
                    .version(Version.Main.PRODUCTION)
                    .net(new Net(bindIp, port, Network.localhostIsIPv6()))
                    .build()

            MongodExecutable mongodExecutable = MongodStarter.defaultInstance.prepare(mongodConfig)
            processes.add(mongodExecutable.start())
        }
    }

    void cleanup() {
        processes*.stop()
    }
}

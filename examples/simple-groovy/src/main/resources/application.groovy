particle.server.port=8080
particle.server.executors.io.type = "fixed"
particle.server.executors.io.nThreads = 75
hibernate {
    hbm2ddl {
        auto = "create-drop"
    }
}
galecino.servo.trim="0.3"
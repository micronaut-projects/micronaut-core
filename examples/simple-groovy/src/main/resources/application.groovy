//micronaut.server.port=8080
micronaut.server.executors.io.type = "fixed"
micronaut.server.executors.io.nThreads = 75
hibernate {
    hbm2ddl {
        auto = "create-drop"
    }
}
galecino.servo.trim="0.3"
# Grails Http Client Example

## Introduction

This project contains a set of sample Grails projects
demonstrating the use of the Micronaut HTTP Client in
a Grails app.

## Consul

The sample may be easily demonstrated using Consul.
Make sure Consul is running on port 8500 of localhost.
[Docker](http://docker.com) may be used to easily start Consul.

`docker run -p 8500:8500 consul`

## Start The Backend Grails Service

The `backend` Grails application publishes a REST API
at `/synths` and by default will run on port 8086.

`./gradlew backend:bootRun`

Once the application is up and running you should see
it registered in Consul under the name `synthData`.  Check
at http://localhost:8500/.

## Start The UI

The `ui` Grails application is a `web` profile app which
will retrieve data from the `synthData` service registered in Consul.

./gradlew ui:bootRun`

Once the application is up and running send a request to
http://localhost:8080/synthesizer/ which should render
synthesizer data retrieved from the remote service.
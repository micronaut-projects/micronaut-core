# Micronaut Petstore

## Summary

The petstore is an example application built on Micronaut in the form of Microservices architecture. The features of the 
application will be familiar to the Java community where pet profiles are displayed. Users can see a list of pets and enter a comment about the pets.
Vendors are selling pets for a price and also notifications by email are included.

The purpose of the petstore application is to demonstrate how Micronaut can be utilized in a Microservice architecture. Each 
application has its own database, project structure, build cycle and resulting artifact. In order to demonstrate the ability of 
Micronaut to integrate with different technologies, the example is wired to various database implementations and developed with various
programming languages. 

## Architecture

The architecture of the pet store begins with the `frontend` project, which is the UI to the application. The `frontend` will 
communicate with the `storefront` project, which acts s a gateway to the rest of the Microservice applications. The Microservice
applications are broken up by cohesive functionality. For example `pets` will focus on the displaying and storing pets, where
`vendors` will do the same for vendors. `Consul` is used for service discovery of each Microservice provider. Each Microservice
can subsequently communicate with each other through `consul` using `HTTP`.

See the diagram below:

![Petstore Architecture](petstore.png?raw=true "petstore architecture")
## Micronaut Installation

In the case you want Micronaut installed locally, go the Micronaut root folder and use gradle to install the Micronaut framework 
dependencies into local maven.

```
./gradlew pTML
```

## Use Docker

Using docker can help get the application up and running quite quickly. 

> _Note that if using a windows version of docker (specifically pre Windows 10 Pro) that `localhost` will be referred to 
as `192.168.99.100`.  Anywhere in the petstore configuration when `localhost` is referenced, 
typically in `application.*` or index.js files, this must be changed. `docker-machine ip` will give you the correct host._ 

## Running Consul and Databases

`consul` needs to be running on port 8500.
- Access `consul` at [http://localhost:8500/](http://localhost:8500/).

`mongo` needs to be running on port 27017.

`neo4j` needs to be running on port 7687.

`redis` needs to be running on port 6379.

With docker you can start them all up in a container: 

```
docker-compose up consul mongodb neo4j redis
```

## Running Application with Gradle

The applications depend on one another so gradle builds need to be run in order:

```
./gradlew pets:run
./gradlew vendors:run
```

Once those are running the rest may be started individually or as a group.
```
./gradlew comments:run mail:run offers:run storefront:run --parallel
```

Finally run the `frontend`, which is written with `React`

```
./gradlew frontend:react:start
```

## Running Application with Docker

The application is easily built and run in a docker container with the command below. However, note you will not be able to 
debug with breakpoints etc (a really handy way to learn Micronaut).

```
./gradlew build -x test
docker-compose build
docker-compose up
```

Wait till all the applications register with consul. 

## Open Petstore

Access the app at [http://localhost:3000/](http://localhost:3000/).

> Note on windows possibly reference the ip for `localhost`
# beacon-network-backend

###### Jakarta EE Platform 10
The implementation is developed and deployed on the [WildFly 30.0.0](http://wildfly.org/) server and is based on Jakarta RESTful Web Services 3.0 API ([JAX-RS 3.0](https://jakarta.ee/specifications/restful-ws/3.0/)).

###### Beacon v2 Java implementation
The implementation uses [Beacon v2 Java beacon-frameworky](https://github.com/elixir-europe/java-beacon-v2.api) model classes.

###### SQL Database
The Beacon Network Aggregator uses [Jakarta Persistence 3.1](https://jakarta.ee/specifications/persistence/3.1/) for logging.
The connection is defined in [persistence.xml](https://github.com/elixir-europe/beacon-network-backend/blob/master/src/main/resources/META-INF/persistence.xml).
Although the Aggregator may be used with any SQL database, it is configured to be used with [PostgreSQL](https://www.postgresql.org/) database

###### Docker Image
This repository is configured to automatically generate docker images on release tags ('vX.Y.Z').  
https://github.com/elixir-europe/beacon-network-backend/pkgs/container/beacon-network-backend  
The image is based on official WildFly image that is extended with PostgreSQL driver and predeployed Beacon Network application.
There is also a [docker-compose.yaml](https://github.com/elixir-europe/beacon-network-backend/blob/master/docker/docker-compose.yaml) receipe which provides easy deployment.

###### Apache Maven build system
The build process is based on [Apache Maven](https://maven.apache.org/).

Compiling:
```shell
>git clone https://github.com/elixir-europe/beacon-network-backend.git
>cd beacon-network-backend
>mvn install
```
This must create `beacon-network-v2-x.x.x.war` (**W**eb application **AR**chive) application in the `/target` directory.

###### BSC Maven Repository
In addition to the provided docker images, artifacts are stored on the Barcelona Supercomputing Center's maven repository:  
https://inb.bsc.es/maven/es/bsc/inb/ga4gh/beacon-network-v2/0.0.9/beacon-network-v2-0.0.9.war

###### Configuration
There are three configuration files in the `/BEACON-INF` directory:
* `configuration.json` - standard beacon configuration file: [beaconConfigurationResponse.json](https://github.com/ga4gh-beacon/beacon-v2/blob/main/framework/json/responses/beaconConfigurationResponse.json)
* `beacon-info.json` - standard beacon information file: [beaconInfoResponse.json](https://github.com/ga4gh-beacon/beacon-v2/blob/main/framework/json/responses/beaconInfoResponse.json)
* `beacon-network.json` - Json Array of backed Beacons' endpoints  

The example of the `beacon-network.json`:
```json
[
  "https://beacons.bsc.es/beacon/v2.0.0",
  "https://ega-archive.org/test-beacon-apis/cineca"
]
```
Note that the **W**eb application **AR**chive (WAR) is just a usual ZIP file so one can edit these configurations manually without the need to rebuild the application.

It is also possible to define external directory for the `beacon-network.json` configuration.
```bash
export BEACON_NETWORK_CONFIG_DIR=/wildfly/BEACON-INF
```
When the `BEACON_NETWORK_CONFIG_DIR` is set, the aggregator monitors the `$BEACON_NETWORK_CONFIG_DIR/beacon-network.json` to dynamically update the configuration.

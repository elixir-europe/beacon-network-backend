# beacon-network-backend

###### Jakarta EE Platform 10
The implementation is developed and deployed on the [WildFly 39.0.0](http://wildfly.org/) server and is based on Jakarta RESTful Web Services 3.1 API ([JAX-RS 3.1](https://jakarta.ee/specifications/restful-ws/3.1/)).

###### Beacon v2 Java implementation
The implementation uses [Beacon v2 Java beacon-framework](https://github.com/elixir-europe/java-beacon-v2.api) model classes.

## Installation

### Docker Image (recommended)
This repository is configured to automatically generate [docker images](https://github.com/elixir-europe/beacon-network-backend/pkgs/container/beacon-network-backend) on release tags ('vX.Y.Z'). The image is based on official WildFly image that is extended with PostgreSQL driver and predeployed Beacon Network application.

To have an easy deployment, go to the [docker folder](./docker) and run [docker compose](https://docs.docker.com/compose/):

```
docker-compose up -d
```

### Manual installation

#### Apache Maven build system
The build process is based on [Apache Maven](https://maven.apache.org/).

Compiling:
```shell
git clone -b dev https://github.com/elixir-europe/beacon-network-backend.git
cd beacon-network-backend
mvn install
```
This must create `beacon-network-v2-x.x.x.war` (**W**eb application **AR**chive) application in the `/target` directory. Alternatively, you can find this file in the Barcelona Supercomputing Center's [maven repository](https://inb.bsc.es/maven/es/bsc/inb/ga4gh/beacon-network-v2/0.0.16/beacon-network-v2-0.0.16-SNAPSHOT.war).

#### WilfFly server
WildFly is a free opensource JEE server and may be easy downloaded from it's website: http://wildfly.org/.  
Nevertheless, the sever requires some configuration which in a case of docker image is done by the [Dcokerfile](https://github.com/elixir-europe/beacon-network-backend/blob/2d42fa703742de713c238a3c2e2e3e5bc6e2c4c7/docker/Dockerfile#L15) recipe.

The Beacon Network logging is implemented using [Jakarta Persistence 3.1](https://jakarta.ee/specifications/persistence/3.1/) and relies on [PostgreSQL](https://www.postgresql.org/) database.
The server must be pre-configured for the PostgreSQL and the PosgreSQL JDBC driver must be intalled into the WildFly (docker image recipe does this job).

The deployment is as simple:

```shell
# Copy .war file to wildfly
cp target/beacon-network-v2-x.x.x.war $WILDFLY_HOME/standalone/deployments/
# Run the application server
./$WILDFLY_HOME/bin/standalone.sh
```

## Configuration

There are three default configuration files in the `/BEACON-INF` directory:
* `beacon-network-configuration.json` - standard beacon configuration file: [beaconConfigurationResponse.json](https://github.com/ga4gh-beacon/beacon-v2/blob/main/framework/json/responses/beaconConfigurationResponse.json)
* `beacon-network-info.json` - standard beacon information file: [beaconInfoResponse.json](https://github.com/ga4gh-beacon/beacon-v2/blob/main/framework/json/responses/beaconInfoResponse.json)
* `beacon-network.json` - Json Array of backed Beacons' endpoints  

The example of the `beacon-network.json`:
```json
[
  "https://beacons.bsc.es/beacon/v2.0.0",
  "https://ega-archive.org/test-beacon-apis/cineca"
]
```
Note that the **W**eb application **AR**chive (WAR) is just a usual ZIP file so one can edit these configurations manually without the need to rebuild the application. The same with Docker, it is automatically updated with new beacons.

It is also possible to define external directory for the configuration.
```bash
export BEACON_NETWORK_CONFIG_DIR=/wildfly/BEACON-INF
```
When the `BEACON_NETWORK_CONFIG_DIR` is set, the aggregator monitors the `$BEACON_NETWORK_CONFIG_DIR/beacon-network.json` to dynamically update the configuration.  
It also looks (but not actively monitoring) the `$BEACON_NETWORK_CONFIG_DIR/beacon-network-configuration.json` and `$BEACON_NETWORK_CONFIG_DIR/beacon-network-info.json` so deployers may change the beacon identifier and other metatada.

It is possible to provide an environment variable `BEACON_NETWORK_LOG_FILE` for the log file.

There are several timeouts that may be configured via environment variables:
- `BEACON_NETWORK_REFRESH_METADATA_TIMEOUT` - timeout in minutes (default 60 min.) Beacon Network reloads metadata of the backed Beacons.
- `BEACON_NETWORK_DISCARD_REQUEST_TIMEOUT` - timeout in seconds (default 5 sec.) after which the response from a Beacon is discarded.
- `BEACON_NETWORK_REQUEST_TIMEOUT` - timeout in seconds (default 600 sec.) after which the request to the Beacon is cancelled.

Note that although responses that take more than `BEACON_NETWORK_DISCARD_REQUEST_TIMEOUT` are discarded (not included in the Beacon Network response), they are not cancelled.
If a long answering Beacon responds before the `BEACON_NETWORK_REQUEST_TIMEOUT`, the result still may be logged.

#### Beacon Network Endpoints pre-configuration

By default, Beacon Network defines the endpoints basing on the endpoints found in the backed beacons (specified in the `/map` endpoint).
Sometimes this could be inconvenient, so there is a way to provide default, preferred endpoint names. Deployers may provide a custom `BEACON_NETWORK_CONFIG_DIR/beacon-network-map.json` template file
(along with `beacon-network.json` file) to trick the generated endpoints:

```json
{
  "response": {
    "endpointSets": {
      "individual": {
        "entryType": "individual",
        "rootUrl": "/individuals2"
      }
    }
  }
}
```

This is a template file (in the format of the Beacon's `/map` schema), where it is possible to remap entire scopes of just a particular endpoint within a scope. 
Here above, all 'individual' endpoints will be redefined:

```json
"individual": {
  "endpoints": {
    "genomicVariant": {
      "returnedEntryType": "genomicVariant",
      "url": "https://beacons.bsc.es/beacon-network/v2.0.0/individuals2/{id}/g_variants"
    },
    "run": {
      "returnedEntryType": "run",
      "url": "https://beacons.bsc.es/beacon-network/v2.0.0/individuals2/{id}/runs"
    },
    "biosample": {
      "returnedEntryType": "biosample",
      "url": "https://beacons.bsc.es/beacon-network/v2.0.0/individuals2/{id}/biosamples"
    },
    "analysis": {
      "returnedEntryType": "analysis",
      "url": "https://beacons.bsc.es/beacon-network/v2.0.0/individuals2/{id}/analyses"
    }
  },
  "entryType": "individual",
  "rootUrl": "https://beacons.bsc.es/beacon-network/v2.0.0/individuals2",
  "singleEntryUrl": "https://beacons.bsc.es/beacon-network/v2.0.0/individuals2/{id}"
}
```
### OpenID Connect security

To improve security, the aggregator may use the Token Exchange ([RFC8693](https://datatracker.ietf.org/doc/html/rfc8693)) 
mechanism in order to limit the scope of token propagation to the backing Beacons. In order to enable this functionality
the proper environment variables must be provided:
```
BEACON_NETWORK_OIDC_ENDPOINT
BEACON_NETWORK_CLIENT_ID
BEACON_NETWORK_CLIENT_SECRET
```
If configured, for each Beacon participating in the Beacon Network the original Bearer token is exchanged with one issued by 
the Beacon Network aggregator client.  

The example of typical token exchange call:
```bash
curl -d "client_id=$CLIENT_ID" \
-d "client_secret=$CLIENT_SECRET" \
-d "subject_token=$ACCESS_TOKEN" \
-d "scope=openid ga4gh_passport_v1 email" \
-d "resource=https://beacons.bsc.es/beacon/v2.0.0" \
--data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
--data-urlencode "requested_token_type=urn:ietf:params:oauth:token-type:access_token" \
https://login.aai.lifescience-ri.eu/oidc/token
```
Note that with each token exchange Beacon Network passes the Beacon's API endpoint (as defined in the `beacon-network.json` file).
Beacon Newtork aggregator's client may be configured to use this information to adjust the returned token accordingly 
(e.g. using Resource Indicators ([RFC8707](https://datatracker.ietf.org/doc/html/rfc8707)).
Special words about the scopes. Token Exchange shouldn't permit scope upscaling. Beacon Network aggregator only requests those scopes that 
are found in the original access token. Moreover, it checks whether these scopes are supported by the client. This is done via requesting a 
"client token" to the Beacon Network aggregator's client:
```bash
curl -H "Content-Type: application/x-www-form-urlencoded" 
-d "grant_type=client_credentials" 
-d "client_id=$CLIENT_ID" 
-d 'client_secret=$CLIENT_SECRET' 
"https://login.aai.lifescience-ri.eu/oidc/token"
```
and checking the scopes provided in this token. This means that the client should allow `client_credentials` grant types 
(in addition to the `urn:ietf:params:oauth:grant-type:token-exchange`).

Beacon Network may also act as a 'nested' Beacon in the other Beacon Network. It may require to validate OIDC Access Token(s).

```
BEACON_NETWORK_TOKEN_ISSUER=https://login.aai.lifescience-ri.eu/oidc
BEACON_NETWORK_TOKEN_AUDIENCE='["client1", "client2"]'
BEACON_NETWORK_TOKEN_AUDIENCE_API_URI_CHECK=true
```
Having `BEACON_NETWORK_TOKEN_ISSUER` property set up enables tokens validation...
If `BEACON_NETWORK_TOKEN_AUDIENCE` property is defined, Beacon Network validates whether some of the valid 'clients' are in the token's audience field...  
The `BEACON_NETWORK_TOKEN_AUDIENCE` value may be either JSON String (BEACON_NETWORK_TOKEN_AUDIENCE='"client"'), JSON Array or a plain value (BEACON_NETWORK_TOKEN_AUDIENCE=client)   
Setting `BEACON_NETWORK_TOKEN_AUDIENCE_API_URI_CHECK` to the 'true' additionally checks whether the Beaon Network's API URL is in the token's audience.

### SQL Database

The Beacon Network Aggregator uses [Jakarta Persistence 3.1](https://jakarta.ee/specifications/persistence/3.1/) for logging.
The connection is defined in [persistence.xml](https://github.com/elixir-europe/beacon-network-backend/blob/master/src/main/resources/META-INF/persistence.xml).
Although the Aggregator may be used with any SQL database, it is configured to be used with [PostgreSQL](https://www.postgresql.org/) database.

The application provides simple SQL logging which level may be confirured via `BEACON_NETWORK_LOG_LEVEL` environment variable.  
The possible values are "**NONE**", "**METADATA**", "**REQUESTS**", "**RESPONSES**", "**ALL**"
- "**NONE**" : No logging at all.
- "**METADATA**" : Only backed beacons' metadata is logged (good for debugging).
- "**AUTH**" : OIDC Authentication calls are logged (OIDC Identity Provider's calls).
- "**QUERIES**" : Beacon Network request quieries are logged.
- "**REQUESTS**" : Beacon Request quieries are logged. It also logs response codes (but not the data).
- "**RESPONSES**" : Logs all Requests with Responses as well as possible error messages.
- "**ALL**" : Maximum logging level. Currently same as "**RESPONSES**"


FROM maven:3.9.11-eclipse-temurin-17 as builder

COPY pom.xml .

RUN mvn dependency:go-offline --no-transfer-progress

COPY src/ /src/

RUN mvn clean install -DskipTests --no-transfer-progress

# Beacon Network 2.0 image
FROM quay.io/wildfly/wildfly:36.0.1.Final-jdk17

# add PostgreSQL java driver and latest Yasson snapshot
COPY ./docker/wildfly/modules/postgresql $JBOSS_HOME/modules/system/layers/base/org/postgresql/
COPY ./docker/wildfly/modules/yasson $JBOSS_HOME/modules/system/layers/base/org/eclipse/yasson/

RUN /bin/sh -c 'echo -e "embed-server --std-out=echo\n \
/subsystem=undertow/server=default-server/ajp-listener=ajp:add(socket-binding=ajp, scheme=https, redirect-socket=https, enabled=true)\n \
/subsystem=undertow/server=default-server/http-listener=default:write-attribute(name=proxy-address-forwarding, value=true)\n \
/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-xa-datasource-class-name=org.postgresql.xa.PGXADataSource)\n \
/subsystem=datasources/data-source=BeaconNetwork:add(driver-name=postgresql,jndi-name=java:jboss/datasources/BeaconNetwork,connection-url=\${env.BEACON_NETWORK_DB_CONNECTION_URL},user-name=\${env.BEACON_NETWORK_DB_USERNAME},password=\${env.BEACON_NETWORK_DB_PASSWORD})\n \
stop-embedded-server" > $JBOSS_HOME/bin/wildfly-config.cli'

RUN $JBOSS_HOME/bin/jboss-cli.sh --file="$JBOSS_HOME/bin/wildfly-config.cli"
RUN rm -rf $JBOSS_HOME/standalone/configuration/standalone_xml_history/current/*

COPY --from=builder /target/beacon-network-v2-*.war $JBOSS_HOME/standalone/deployments/beacon-network-v2.war

# Expose AJP port
EXPOSE 8009

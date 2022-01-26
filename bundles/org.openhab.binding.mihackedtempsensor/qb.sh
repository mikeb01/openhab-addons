#!/usr/bin/env bash

mvn clean
mvn -DskipChecks -Dspotless.check.skip=true clean install -pl :org.openhab.binding.mihackedtempsensor && cp ./org.openhab.binding.mihackedtempsensor/target/org.openhab.binding.mihackedtempsensor-3.3.0-SNAPSHOT.jar ~/opt/openhab/addons/.

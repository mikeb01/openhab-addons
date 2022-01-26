#!/usr/bin/env bash

mvn spotless:apply -pl :org.openhab.binding.mihackedtempsensor && mvn clean install -pl :org.openhab.binding.mihackedtempsensor && cp ./org.openhab.binding.mihackedtempsensor/target/org.openhab.binding.mihackedtempsensor-3.3.0-SNAPSHOT.jar ~/opt/openhab/addons/.

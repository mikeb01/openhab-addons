/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mihackedtempsensor.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MiHackedTempSensorHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Michael Barker - Initial contribution
 */
@NonNullByDefault
public class MiHackTempSensorDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(MiHackTempSensorDiscoveryService.class);
    @Nullable
    private MiHackedTempSensorBridgeHandler bridgeHandler = null;

    public MiHackTempSensorDiscoveryService() {
        super(Set.of(MiHackedTempSensorBindingConstants.SENSOR_TYPE_ID), 45, false);
    }

    protected void startScan() {
        logger.debug("Bridge: {}", bridgeHandler);
        if (null != bridgeHandler) {
            bridgeHandler.addDeviceListener(this);
        }
    }

    protected synchronized void stopScan() {
        if (null != bridgeHandler) {
            bridgeHandler.removeDeviceListener(this);
        }
    }

    public void setThingHandler(final ThingHandler thingHandler) {
        logger.debug("Setting bridge handler");
        if (thingHandler instanceof MiHackedTempSensorBridgeHandler) {
            logger.debug("Setting bridge handler");
            bridgeHandler = (MiHackedTempSensorBridgeHandler) thingHandler;
        }
    }

    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }

    public void activate() {
        logger.debug("Activating");
        ThingHandlerService.super.activate();
    }

    public void deactivate() {
        ThingHandlerService.super.deactivate();
    }

    public void onDevice(final SensorValues sensorValues) {
        if (null != bridgeHandler) {
            final DiscoveryResult discoveryResult = DiscoveryResultBuilder
                    .create(new ThingUID(MiHackedTempSensorBindingConstants.SENSOR_TYPE_ID, sensorValues.getUniqueId()))
                    .withProperty("macAddress", sensorValues.getAddress())
                    .withProperty("uniqueId", sensorValues.getUniqueId())
                    .withBridge(bridgeHandler.getThing().getBridgeUID()).withRepresentationProperty("uniqueId").build();

            thingDiscovered(discoveryResult);
        }
    }
}

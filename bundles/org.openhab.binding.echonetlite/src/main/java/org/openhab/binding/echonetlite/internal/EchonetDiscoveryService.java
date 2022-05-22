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
package org.openhab.binding.echonetlite.internal;

import static org.openhab.binding.echonetlite.internal.EchonetLiteBindingConstants.THING_TYPE_ECHONET_DEVICE;

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
 * @author Michael Barker - Initial contribution
 */
@NonNullByDefault
public class EchonetDiscoveryService extends AbstractDiscoveryService
        implements EchonetDiscoveryListener, ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(EchonetDiscoveryService.class);

    @Nullable
    private EchonetLiteBridgeHandler bridgeHandler;

    public EchonetDiscoveryService() {
        super(Set.of(THING_TYPE_ECHONET_DEVICE), 10);
    }

    @Override
    protected void startScan() {
        logger.debug("startScan: {}", bridgeHandler);
        if (null != bridgeHandler) {
            bridgeHandler.startDiscovery(this);
        } else {
            logger.error("bridgeHandler not initialized");
        }
    }

    @Override
    protected synchronized void stopScan() {
        if (null != bridgeHandler) {
            bridgeHandler.stopDiscovery();
        }
    }

    @Override
    public void onDeviceFound(String identifier, InstanceKey instanceKey) {

        if (null == bridgeHandler) {
            return;
        }

        final DiscoveryResult discoveryResult = DiscoveryResultBuilder
                .create(new ThingUID(THING_TYPE_ECHONET_DEVICE, bridgeHandler.getThing().getUID(), identifier))
                .withProperty("instanceKey", instanceKey.representationProperty())
                .withProperty("hostname", instanceKey.address.getAddress().getHostAddress())
                .withProperty("port", instanceKey.address.getPort())
                .withProperty("groupCode", instanceKey.klass.groupCode())
                .withProperty("classCode", instanceKey.klass.classCode()).withProperty("instance", instanceKey.instance)
                .withBridge(bridgeHandler.getThing().getUID()).withRepresentationProperty("instanceKey").build();
        thingDiscovered(discoveryResult);
    }

    @Override
    public void deactivate() {
        ThingHandlerService.super.deactivate();
    }

    @Override
    public void activate() {
        ThingHandlerService.super.activate();
    }

    @Override
    public void setThingHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof EchonetLiteBridgeHandler) {
            this.bridgeHandler = (EchonetLiteBridgeHandler) thingHandler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }
}

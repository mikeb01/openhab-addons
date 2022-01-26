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

import static org.openhab.binding.mihackedtempsensor.internal.MiHackedTempSensorBindingConstants.MI_PATH_PATTERN;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bluez.Adapter1;
import org.bluez.exceptions.BluezFailedException;
import org.bluez.exceptions.BluezNotAuthorizedException;
import org.bluez.exceptions.BluezNotReadyException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.freedesktop.dbus.DBusMatchRule;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.ObjectPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MiHackedTempSensorHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Michael Barker - Initial contribution
 */
@NonNullByDefault
public class MiHackedTempSensorBridgeHandler extends BaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(MiHackedTempSensorBridgeHandler.class);
    private final DBusMatchRule matchingRule = new DBusMatchRule("signal", "org.freedesktop.DBus.ObjectManager",
            "InterfacesAdded");
    private final DBusSigHandler<DBusSignal> sigHandler = this::handleSignal;
    private final Map<String, SensorValues> cachedValues = new HashMap<>();
    private final Object mutex = new Object();

    @Nullable
    private ScheduledFuture<?> backgroundRunningFuture = null;
    @Nullable
    private MiHackTempSensorDiscoveryService discoveryService = null;
    @Nullable
    private DBusConnection connection;

    public MiHackedTempSensorBridgeHandler(final Bridge bridge) {
        super(bridge);
    }

    public void initialize() {

        updateStatus(ThingStatus.UNKNOWN);

        try {
            connection = DBusConnection.getConnection(DBusConnection.DBusBusType.SYSTEM);
        } catch (DBusException e) {
            logger.error("Failed to connect to DBus", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Failed to connect to dbus");
            return;
        }

        try {
            connection.addGenericSigHandler(matchingRule, sigHandler);
        } catch (DBusException e) {
            logger.error("Failed to adding signal handler", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Failed to add signal handler");
            return;
        }

        updateStatus(ThingStatus.ONLINE);
        backgroundRunningFuture = scheduler.scheduleWithFixedDelay(this::pollDevices, 10, 30, TimeUnit.SECONDS);
    }

    public void dispose() {

        synchronized (mutex) {

            if (null != backgroundRunningFuture) {
                logger.info("Cancelling background thread: {}", backgroundRunningFuture.cancel(false));
            }

            stopDiscovery();

            try {
                if (null != connection) {
                    connection.removeGenericSigHandler(matchingRule, sigHandler);
                }
            } catch (DBusException e) {
                logger.error("Failed to remove signal handler", e);
            }

            try {
                connection.close();
            } catch (IOException e) {
                logger.error("Failed to close connection to DBus", e);
            }

            connection = null;
        }
    }

    private void stopDiscovery() {
        synchronized (mutex) {
            try {
                if (null != connection) {
                    final Adapter1 adapter1 = connection.getRemoteObject("org.bluez", "/org/bluez/hci0",
                            Adapter1.class);
                    final Properties adapter1Properties = connection.getRemoteObject("org.bluez", "/org/bluez/hci0",
                            Properties.class);
                    Boolean isDiscovering = null;
                    try {
                        isDiscovering = adapter1Properties.Get("org.bluez.Adapter1", "Discovering");
                        logger.debug("Stopping discovery");
                        adapter1.StopDiscovery();
                    } catch (BluezNotReadyException | BluezFailedException | BluezNotAuthorizedException
                            | DBusExecutionException e) {
                        logger.error("Failed to stopDiscovery, Discovering={}", isDiscovering, e);
                    }
                }
            } catch (DBusException e) {
                logger.error("Failed to get adapter interface to stopDiscovery", e);
            }
        }
    }

    private void handleSignal(DBusSignal dBusSignal) {
        try {
            final Object[] parameters = dBusSignal.getParameters();
            if (isMiTempSensor(parameters)) {
                @SuppressWarnings("unchecked")
                final Map<String, Map<String, Variant<?>>> map = (Map<String, Map<String, Variant<?>>>) parameters[1];
                try {
                    onDeviceUpdate(SensorValues.create(map));
                } catch (IllegalArgumentException e) {
                    logger.warn("Failed to extract device properties", e);
                }
            }

        } catch (DBusException e) {
            logger.error("Failed to get dbus event parameters", e);
        }
    }

    private void pollDevices() {

        synchronized (mutex) {

            if (null == connection) {
                return;
            }

            try {
                final ObjectManager objectManager = connection.getRemoteObject("org.bluez", "/", ObjectManager.class);

                logger.debug("Querying object manager");
                final Map<DBusPath, Map<String, Map<String, Variant<?>>>> dBusPathMapMap = objectManager
                        .GetManagedObjects();

                dBusPathMapMap.forEach((k, v) -> {
                    if (MI_PATH_PATTERN.matcher(k.getPath()).matches()) {
                        try {
                            onDeviceUpdate(SensorValues.create(v));
                        } catch (IllegalArgumentException e) {
                            logger.warn("Failed to extract device properties", e);
                        }
                    }
                });
            } catch (DBusException e) {
                logger.error("Failed to query existing devices", e);
            }

            try {
                final Adapter1 adapter1 = connection.getRemoteObject("org.bluez", "/org/bluez/hci0", Adapter1.class);
                final Properties adapter1Properties = connection.getRemoteObject("org.bluez", "/org/bluez/hci0",
                        Properties.class);
                Boolean isDiscovering = null;

                Map<String, Variant<?>> filter = new HashMap<>();

                filter.put("Transport", new Variant<>("le"));
                adapter1.SetDiscoveryFilter(filter);

                try {
                    logger.debug("Starting discovery");
                    isDiscovering = adapter1Properties.Get("org.bluez.Adapter1", "Discovering");
                    adapter1.StartDiscovery();
                } catch (DBusExecutionException e) {
                    logger.error("StartDiscovery failed: {}, {}, {}", isDiscovering, e.getType(), e.getMessage());
                } finally {
                    scheduler.schedule(this::stopDiscovery, 10, TimeUnit.SECONDS);
                }
            } catch (DBusException e) {
                logger.error("Failed to poll devices", e);
            }
        }
    }

    public void handleCommand(final ChannelUID channelUID, final Command command) {
    }

    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singletonList(MiHackTempSensorDiscoveryService.class);
    }

    public void onDeviceUpdate(final SensorValues sensorValues) {
        logger.debug("{}", sensorValues);

        cachedValues.put(sensorValues.getAddress(), sensorValues);
        if (null != discoveryService) {
            discoveryService.onDevice(sensorValues);
        }

        final String uniqueId = sensorValues.getUniqueId();
        final List<Thing> things = getThing().getThings();
        for (Thing thing : things) {
            if (Objects.equals(thing.getUID().getId(), uniqueId)) {
                final MiHackedTempSensorHandler handler = (MiHackedTempSensorHandler) thing.getHandler();
                if (null != handler) {
                    handler.update(sensorValues);
                }
            }
        }
    }

    private static boolean isMiTempSensor(final Object[] parameters) {
        for (Object parameter : parameters) {
            if (parameter instanceof ObjectPath) {
                ObjectPath op = (ObjectPath) parameter;
                if (MI_PATH_PATTERN.matcher(op.getPath()).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addDeviceListener(final MiHackTempSensorDiscoveryService miHackTempSensorDiscoveryService) {
        this.discoveryService = miHackTempSensorDiscoveryService;
        cachedValues.values().forEach(discoveryService::onDevice);
    }

    public void removeDeviceListener(final MiHackTempSensorDiscoveryService miHackTempSensorDiscoveryService) {
        if (Objects.equals(this.discoveryService, miHackTempSensorDiscoveryService)) {
            this.discoveryService = null;
        }
    }
}

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

import static org.openhab.binding.mihackedtempsensor.internal.MiHackedTempSensorBindingConstants.DEVICE_STATUS_UUID;
import static org.openhab.binding.mihackedtempsensor.internal.MiHackedTempSensorBindingConstants.MI_PATH_PATTERN;
import static org.openhab.binding.mihackedtempsensor.internal.MiHackedTempSensorBindingConstants.SENSOR_TYPE_ID;

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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.freedesktop.dbus.DBusMap;
import org.freedesktop.dbus.DBusMatchRule;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.ObjectPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
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

    @Nullable
    private ScheduledFuture<?> backgroundRunningFuture = null;
    @Nullable
    private MiHackTempSensorDiscoveryService discoveryService = null;

    public MiHackedTempSensorBridgeHandler(final Bridge bridge) {
        super(bridge);
    }

    public void initialize() {
        logger.info("initialise");
        updateStatus(ThingStatus.ONLINE);
        backgroundRunningFuture = scheduler.scheduleWithFixedDelay(this::pollDevices, 30, 30, TimeUnit.SECONDS);
    }

    public void dispose() {
        if (null != backgroundRunningFuture) {
            logger.info("Cancelling background thread: {}", backgroundRunningFuture.cancel(false));
        }
    }

    private void handleSignal(DBusSignal dBusSignal) {
        try {
            final Object[] parameters = dBusSignal.getParameters();
            if (isMiTempSensor(parameters)) {
                final Map<String, Map<String, Variant<?>>> map = (Map<String, Map<String, Variant<?>>>) parameters[1];
                try {
                    onDeviceUpdate(readProperties(map));
                } catch (IllegalArgumentException e) {
                    logger.warn("Failed to extract device properties", e);
                }
            }

        } catch (DBusException e) {
            logger.error("Failed to get dbus event parameters", e);
        }
    }

    private void pollDevices() {
        logger.debug("Polling");
        try (DBusConnection cn = DBusConnection.getConnection(DBusConnection.DBusBusType.SYSTEM)) {

            try {
                cn.addGenericSigHandler(matchingRule, sigHandler);
                final ObjectManager objectManager = cn.getRemoteObject("org.bluez", "/", ObjectManager.class);

                final Map<DBusPath, Map<String, Map<String, Variant<?>>>> dBusPathMapMap = objectManager
                        .GetManagedObjects();

                dBusPathMapMap.forEach((k, v) -> {
                    if (MI_PATH_PATTERN.matcher(k.getPath()).matches()) {
                        try {
                            onDeviceUpdate(readProperties(v));
                        } catch (IllegalArgumentException e) {
                            logger.warn("Failed to extract device properties", e);
                        }
                    }
                });

                final Adapter1 adapter1 = cn.getRemoteObject("org.bluez", "/org/bluez/hci0", Adapter1.class);

                Map<String, Variant<?>> filter = new HashMap<>();

                filter.put("Transport", new Variant<>("le"));
                adapter1.SetDiscoveryFilter(filter);

                try {
                    adapter1.StartDiscovery();
                    waitForDiscoveredDevices(10_000);
                } catch (DBusExecutionException e) {
                    logger.error("StartDiscovery failed: {}, {}", e.getType(), e.getMessage());
                } finally {
                    try {
                        adapter1.StopDiscovery();
                    } catch (DBusExecutionException e) {
                        logger.error("StopDiscovery failed: {}, {}", e.getType(), e.getMessage());
                    }
                }

            } finally {
                try {
                    cn.removeGenericSigHandler(matchingRule, sigHandler);
                } catch (DBusException e) {
                    logger.error("removeGenericSignalHandler failed", e);
                }
            }

        } catch (IOException | DBusException | DBusExecutionException e) {
            logger.error("DBus poll failed", e);
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

        final ThingUID thingUID = new ThingUID(SENSOR_TYPE_ID, sensorValues.getUniqueId());
        final List<Thing> things = getThing().getThings();
        for (Thing thing : things) {
            if (Objects.equals(thing.getUID(), thingUID)) {
                final MiHackedTempSensorHandler handler = (MiHackedTempSensorHandler) thing.getHandler();
                if (null != handler) {
                    handler.update(sensorValues);
                }
            }
        }
    }

    private static void waitForDiscoveredDevices(final int waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ignore) {
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

    private static SensorValues readProperties(final Map<String, Map<String, Variant<?>>> objectProperties)
            throws IllegalArgumentException {
        final Map<String, Variant<?>> device1Properties = objectProperties.get("org.bluez.Device1");
        if (null == device1Properties) {
            throw new IllegalArgumentException("'org.bluez.Device1' key not found");
        }

        final String address = readProperty(device1Properties, "Address");
        final Short rssi = readProperty(device1Properties, "RSSI");
        final DBusMap<String, Variant<?>> serviceData = readProperty(device1Properties, "ServiceData");
        byte[] data = readProperty(serviceData, DEVICE_STATUS_UUID);

        if (data.length < 13) {
            throw new IllegalArgumentException("Array to short for measured values: " + data.length);
        }

        float temperature = (float) ((data[7] & 0xFF) << 8 | data[6] & 0xFF) / 100.0f;
        float humidity = (float) ((data[9] & 0xFF) << 8 | data[8] & 0xFF) / 100.0f;
        int battery = data[12] & 0xFF;

        return new SensorValues(address, rssi, temperature, humidity, battery);
    }

    private static <T> T readProperty(final Map<String, Variant<?>> properties, final String key) {
        final Variant<?> variant = properties.get(key);
        if (null == variant) {
            throw new IllegalArgumentException("'" + key + "' not found");
        }

        return ((Variant<T>) variant).getValue();
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

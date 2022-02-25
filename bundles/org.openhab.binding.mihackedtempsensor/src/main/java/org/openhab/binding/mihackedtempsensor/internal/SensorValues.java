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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.freedesktop.dbus.DBusMap;
import org.freedesktop.dbus.types.Variant;

/**
 * @author Michael Barker - Initial contribution
 */
@NonNullByDefault
public class SensorValues {
    private final String address;
    private final short rssi;
    private final float temperature;
    private final float humidity;
    private final int batteryLevel;

    public SensorValues(final String address, final short rssi, final float temperature, final float humidity,
            final int batteryLevel) {
        this.address = address;
        this.rssi = rssi;
        this.temperature = temperature;
        this.humidity = humidity;
        this.batteryLevel = batteryLevel;
    }

    static SensorValues create(final Map<String, Map<String, Variant<?>>> objectProperties)
            throws IllegalArgumentException {
        final Map<String, Variant<?>> device1Properties = objectProperties.get("org.bluez.Device1");
        if (null == device1Properties) {
            throw new IllegalArgumentException("'org.bluez.Device1' key not found");
        }

        final String address = readProperty(device1Properties, "Address", null);
        final Short rssi = readProperty(device1Properties, "RSSI", (short)0);
        final DBusMap<String, Variant<?>> serviceData = readProperty(device1Properties, "ServiceData", null);
        byte[] data = readProperty(serviceData, DEVICE_STATUS_UUID, null);

        if (data.length < 13) {
            throw new IllegalArgumentException("Array to short for measured values: " + data.length);
        }

        float temperature = (float) ((data[7] & 0xFF) << 8 | data[6] & 0xFF) / 100.0f;
        float humidity = (float) ((data[9] & 0xFF) << 8 | data[8] & 0xFF) / 100.0f;
        int battery = data[12] & 0xFF;

        return new SensorValues(address, rssi, temperature, humidity, battery);
    }

    static <T> T readProperty(final Map<String, Variant<?>> properties, final String key, @Nullable T defaultValue) {
        final Variant<?> variant = properties.get(key);
        if (null == variant ) {
            if (null == defaultValue) {
                throw new IllegalArgumentException("'" + key + "' not found");
            } else {
                return defaultValue;
            }
        }

        // noinspection unchecked
        return ((Variant<T>) variant).getValue();
    }

    public String toString() {
        return "SensorValues{" + "address='" + address + '\'' + ", rssi=" + rssi + ", temperature=" + temperature
                + ", humidity=" + humidity + ", batteryLevel=" + batteryLevel + '}';
    }

    public String getAddress() {
        return address;
    }

    public String getUniqueId() {
        return address.replace(":", "");
    }

    public short getRssi() {
        return rssi;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getHumidity() {
        return humidity;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }
}

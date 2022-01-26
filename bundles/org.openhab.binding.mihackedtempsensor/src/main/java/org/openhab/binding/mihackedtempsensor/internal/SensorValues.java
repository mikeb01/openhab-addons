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

import org.eclipse.jdt.annotation.NonNullByDefault;

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

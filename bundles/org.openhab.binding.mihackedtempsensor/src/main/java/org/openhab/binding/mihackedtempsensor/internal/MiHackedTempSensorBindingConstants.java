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

import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link MiHackedTempSensorBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Michael Barker - Initial contribution
 */
@NonNullByDefault
public class MiHackedTempSensorBindingConstants {

    private static final String BINDING_ID = "mihackedtempsensor";

    // List of all Thing Type UIDs
    public static final ThingTypeUID SENSOR_TYPE_ID = new ThingTypeUID(BINDING_ID, "sensor");
    public static final ThingTypeUID BRIDGE_TYPE_ID = new ThingTypeUID(BINDING_ID, "dbus-bridge");

    // List of all Channel ids
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_HUMIDITY = "humidity";
    public static final String CHANNEL_BATTERY_LEVEL = "batteryLevel";
    public static final String CHANNEL_RSSI = "rssi";

    public static final Pattern MI_PATH_PATTERN = Pattern
            .compile("/org/bluez/hci0/dev_A4_C1_38_[0-9A-F]{2}_[0-9A-F]{2}_[0-9A-F]{2}");

    public static final String DEVICE_STATUS_UUID = "0000181a-0000-1000-8000-00805f9b34fb";
}

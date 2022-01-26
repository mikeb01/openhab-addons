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

import static org.openhab.binding.mihackedtempsensor.internal.MiHackedTempSensorBindingConstants.CHANNEL_BATTERY_LEVEL;
import static org.openhab.binding.mihackedtempsensor.internal.MiHackedTempSensorBindingConstants.CHANNEL_HUMIDITY;
import static org.openhab.binding.mihackedtempsensor.internal.MiHackedTempSensorBindingConstants.CHANNEL_RSSI;
import static org.openhab.binding.mihackedtempsensor.internal.MiHackedTempSensorBindingConstants.CHANNEL_TEMPERATURE;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;

/**
 * The {@link MiHackedTempSensorHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Michael Barker - Initial contribution
 */
@NonNullByDefault
public class MiHackedTempSensorHandler extends BaseThingHandler {

    @Nullable
    private SensorValues sensorValues;

    public MiHackedTempSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType && null != sensorValues) {
            if (CHANNEL_TEMPERATURE.equals(channelUID.getId())) {
                updateState(CHANNEL_TEMPERATURE, new DecimalType(sensorValues.getTemperature()));
            } else if (CHANNEL_HUMIDITY.equals(channelUID.getId())) {
                updateState(CHANNEL_HUMIDITY, new DecimalType(sensorValues.getHumidity()));
            } else if (CHANNEL_BATTERY_LEVEL.equals(channelUID.getId())) {
                updateState(CHANNEL_BATTERY_LEVEL, new DecimalType(sensorValues.getBatteryLevel()));
            } else if (CHANNEL_RSSI.equals(channelUID.getId())) {
                updateState(CHANNEL_RSSI, new DecimalType(sensorValues.getRssi()));
            }
        }
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.ONLINE);
    }

    public void update(final SensorValues sensorValues) {
        this.sensorValues = sensorValues;
        updateState(CHANNEL_TEMPERATURE,
                new DecimalType(BigDecimal.valueOf(sensorValues.getTemperature()).setScale(2, RoundingMode.HALF_EVEN)));
        updateState(CHANNEL_BATTERY_LEVEL, new DecimalType(sensorValues.getBatteryLevel()));
        updateState(CHANNEL_HUMIDITY,
                new DecimalType(BigDecimal.valueOf(sensorValues.getHumidity()).setScale(2, RoundingMode.HALF_EVEN)));
        updateState(CHANNEL_RSSI, new DecimalType(sensorValues.getRssi()));
    }
}

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
package org.openhab.binding.avmfritz.internal.hardware.callbacks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.avmfritz.internal.handler.AVMFritzBaseBridgeHandler;
import org.openhab.binding.avmfritz.internal.hardware.FritzAhaWebInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Callback for queries for connected devices.
 *
 * @author Michael Barker - Initial Contribution
 */
@NonNullByDefault
public class FritzAhaConnectedDevicesCallback extends FritzAhaReauthCallback {

    private final Logger logger = LoggerFactory.getLogger(FritzAhaConnectedDevicesCallback.class);
    private final AVMFritzBaseBridgeHandler handler;
    private final Map<String, String> formData = new HashMap<>();
    private int retries = 1;

    /**
     * Constructor for retryable authentication
     *
     * @param webIface Web interface to use
     * @param handler Bridge handler to send updates too.
     */
    public FritzAhaConnectedDevicesCallback(final FritzAhaWebInterface webIface,
            final AVMFritzBaseBridgeHandler handler) {
        super("data.lua", "", webIface, HttpMethod.GET, 1);
        this.handler = handler;

        formData.put("xhr", "1");
        formData.put("page", "overview");
        formData.put("xhrId", "all");
        formData.put("useajax", "1");
        formData.put("no_sidrenew", "");
    }

    public void requestDevices() {
        getWebIface().asyncPost(getPath(), formData, this);
    }

    public void execute(int status, String response) {
        boolean validRequest = false;
        if (status != 200 || "".equals(response) || ".".equals(response)) {
            if (retries >= 1) {
                getWebIface().authenticate();
                retries--;
                getWebIface().asyncPost(getPath(), formData, this);
            }
        } else {
            validRequest = true;
        }

        if (validRequest) {
            final JsonElement rootElement = JsonParser.parseString(response);

            final JsonElement dataElement = rootElement.getAsJsonObject().get("data");
            if (null == dataElement || !dataElement.isJsonObject()) {
                logger.warn("'data' is not a valid JsonObject in Fritz!Box main response");
                return;
            }
            final JsonElement netElement = dataElement.getAsJsonObject().get("net");
            if (null == netElement || !netElement.isJsonObject()) {
                logger.warn("'net' is not a valid JsonObject in Fritz!Box main response");
                return;
            }
            final JsonElement devicesElement = netElement.getAsJsonObject().get("devices");
            if (null == devicesElement || !devicesElement.isJsonArray()) {
                logger.warn("'devices' is not a valid JsonArray in Fritz!Box main response");
                return;
            }
            var devicesArray = devicesElement.getAsJsonArray();
            var names = new ArrayList<String>();
            for (JsonElement jsonElement : devicesArray) {
                final JsonElement nameElement = jsonElement.getAsJsonObject().get("name");
                if (null != nameElement) {
                    names.add(nameElement.getAsString());
                }
            }

            handler.connectedDevices(names);
        }
    }
}

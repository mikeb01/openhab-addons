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

import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Barker - Initial contribution
 */
@NonNullByDefault
public abstract class EchonetObject {

    private final Logger logger = LoggerFactory.getLogger(EchonetObject.class);

    protected final InstanceKey instanceKey;
    protected final HashSet<Epc> pendingGets = new HashSet<>();

    @Nullable
    protected InflightRequest inflightGetRequest;
    @Nullable
    protected InflightRequest inflightSetRequest;

    protected long pollIntervalMs;
    protected long retryTimeoutMs;

    public EchonetObject(final InstanceKey instanceKey, final Epc initialProperty) {
        this.instanceKey = instanceKey;
        pendingGets.add(initialProperty);
    }

    public InstanceKey instanceKey() {
        return instanceKey;
    }

    public void applyProperty(InstanceKey sourceInstanceKey, Esv esv, final int epcCode, final int pdc,
            final ByteBuffer edt) {
    }

    public boolean buildPollMessage(final EchonetMessageBuilder messageBuilder, final ShortSupplier tidSupplier,
            long nowMs, InstanceKey managementControllerKey) {
        if (pendingGets.isEmpty()) {
            return false;
        }

        final InflightRequest inflightGetRequest = this.inflightGetRequest;
        if (null == inflightGetRequest) {
            logger.warn("{} has null inflight", instanceKey());
            return false;
        }

        if (hasInflight(nowMs, inflightGetRequest)) {
            return false;
        }

        final short tid = tidSupplier.getAsShort();
        messageBuilder.start(tid, managementControllerKey, instanceKey(), Esv.Get);

        for (Epc pendingProperty : pendingGets) {
            messageBuilder.appendEpcRequest(pendingProperty.code());
        }

        inflightGetRequest.requestSent(tid, nowMs);

        return true;
    }

    protected boolean hasInflight(long nowMs, @Nullable InflightRequest inflightGetRequest) {
        if (null != inflightGetRequest && inflightGetRequest.isInflight()) {
            return !inflightGetRequest.hasTimedOut(nowMs);
        }
        return false;
    }

    protected void setTimeouts(long pollIntervalMs, long retryTimeoutMs) {
        this.pollIntervalMs = pollIntervalMs;
        this.retryTimeoutMs = retryTimeoutMs;
        this.inflightGetRequest = new InflightRequest(TimeUnit.SECONDS.toMillis(1), inflightGetRequest, "GET");
        this.inflightSetRequest = new InflightRequest(TimeUnit.SECONDS.toMillis(1), inflightSetRequest, "SET");
    }

    public boolean buildUpdateMessage(final EchonetMessageBuilder messageBuilder, final ShortSupplier tid,
            final long nowMs, InstanceKey managementControllerKey) {
        return false;
    }

    public void refreshAll(long nowMs) {
    }

    public String toString() {
        return "ItemBase{" + "instanceKey=" + instanceKey + ", pendingProperties=" + pendingGets + '}';
    }

    public void update(String channelId, State state) {
    }

    public void removed() {
    }

    public void refresh(String channelId) {
    }

    public void applyHeader(Esv esv, short tid) {
        if ((esv == Esv.Get_Res || esv == Esv.Get_SNA) && null != inflightGetRequest) {
            final long sentTimestampMs = inflightGetRequest.timestampMs;
            if (inflightGetRequest.responseReceived(tid)) {
                logger.debug("{} response time: {}ms", esv, Clock.systemUTC().millis() - sentTimestampMs);
            } else {
                logger.warn("Unexpected {} response: {}", esv, tid);
                inflightGetRequest.checkOldResponse(tid);
            }
        } else if ((esv == Esv.Set_Res || esv == Esv.SetC_SNA) && null != inflightSetRequest) {
            final long sentTimestampMs = inflightSetRequest.timestampMs;
            if (inflightSetRequest.responseReceived(tid)) {
                logger.debug("{} response time: {}ms", esv, Clock.systemUTC().millis() - sentTimestampMs);
            } else {
                logger.warn("Unexpected {} response: {}", esv, tid);
                inflightSetRequest.checkOldResponse(tid);
            }
        }
    }

    protected static class InflightRequest {
        private static final long NULL_TIMESTAMP = -1;

        private final Logger logger = LoggerFactory.getLogger(InflightRequest.class);
        private final long timeoutMs;
        private final String name;
        private final Map<Short, Long> oldRequests = new HashMap<>();

        private short tid;
        private long timestampMs = NULL_TIMESTAMP;
        @SuppressWarnings("unused")
        private int timeoutCount = 0;

        InflightRequest(long timeoutMs, @Nullable InflightRequest existing, String name) {
            this.timeoutMs = timeoutMs;
            this.name = name;
            if (null != existing) {
                this.tid = existing.tid;
                this.timestampMs = existing.timestampMs;
            }
        }

        void requestSent(short tid, long timestampMs) {
            this.tid = tid;
            this.timestampMs = timestampMs;
        }

        boolean responseReceived(short tid) {
            timestampMs = NULL_TIMESTAMP;
            timeoutCount = 0;

            return this.tid == tid;
        }

        boolean hasTimedOut(long nowMs) {
            final boolean timedOut = timestampMs + timeoutMs <= nowMs;
            if (timedOut) {
                logger.warn("Timed out {}, tid={}, timestampMs={} + timeoutMs={} <= nowMs={}", name, tid, timestampMs,
                        timeoutMs, nowMs);
                timeoutCount++;

                if (NULL_TIMESTAMP != tid) {
                    oldRequests.put(tid, timestampMs);
                }
            }
            return timedOut;
        }

        public boolean isInflight() {
            return NULL_TIMESTAMP != timestampMs;
        }

        public void checkOldResponse(short tid) {
            final Long oldResponseTimestampMs = oldRequests.remove(tid);
            if (null != oldResponseTimestampMs) {
                logger.warn("Timed out request, tid={}, actually took={}", tid,
                        System.currentTimeMillis() - oldResponseTimestampMs);
            }
        }
    }
}

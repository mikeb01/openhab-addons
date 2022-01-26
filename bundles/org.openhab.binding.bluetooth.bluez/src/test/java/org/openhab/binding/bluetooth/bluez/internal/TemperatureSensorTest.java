package org.openhab.binding.bluetooth.bluez.internal;

import org.bluez.Adapter1;
import org.freedesktop.dbus.DBusMap;
import org.freedesktop.dbus.DBusMatchRule;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.ObjectPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.ObjectManager;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class TemperatureSensorTest
{
    @Test
    void name() throws DBusException, IOException, InterruptedException
    {
        final String xaiomiPathPrefix = "/org/bluez/hci0/dev_A4_C1_38_";
        final Pattern pathPattern = Pattern.compile("/org/bluez/hci0/dev_A4_C1_38_[0-9A-F]{2}_[0-9A-F]{2}_[0-9A-F]{2}");
        final String serviceDataKey = "0000181a-0000-1000-8000-00805f9b34fb";

        try (DBusConnection cn = DBusConnection.getConnection(DBusConnection.DBusBusType.SYSTEM))
        {
            cn.addGenericSigHandler(new DBusMatchRule("signal", "org.freedesktop.DBus.ObjectManager", "InterfacesAdded"),
                s -> {
                    Object[] parameters = { };

                    try
                    {
                        parameters = s.getParameters();

                        if (0 < parameters.length && parameters[0] instanceof ObjectPath)
                        {
                            ObjectPath op = (ObjectPath)parameters[0];
                            final String path = op.getPath();
                            if (path.startsWith(xaiomiPathPrefix))
                            {
                                if (1 < parameters.length && parameters[1] instanceof DBusMap) {
                                    final DBusMap<String, DBusMap<String, Variant<?>>> map = (DBusMap<String, DBusMap<String, Variant<?>>>)parameters[1];
                                    final DBusMap<String, Variant<?>> props = map.get("org.bluez.Device1");

                                    final Variant<?> address = props.get("Address");
                                    System.out.println(address + " " + address.getType());
                                    final Variant<?> rssi = props.get("RSSI");
                                    System.out.println(rssi + " " + rssi.getType());

                                    final Variant<DBusMap<String, Variant<byte[]>>> serviceData = (Variant<DBusMap<String, Variant<byte[]>>>)props.get("ServiceData");
                                    final DBusMap<String, Variant<byte[]>> value = serviceData.getValue();
                                    final byte[] value1 = value.get(serviceDataKey).getValue();

                                    printInterpreted(value1);
                                }
                            }
                        }
                    }
                    catch (DBusException e)
                    {
                        e.printStackTrace();
                    }
                });


            final ObjectManager objectManager = cn.getRemoteObject("org.bluez", "/", ObjectManager.class);

            final Map<DBusPath, Map<String, Map<String, Variant<?>>>> dBusPathMapMap = objectManager.GetManagedObjects();
            dBusPathMapMap.forEach(
                (k, v) -> {
                    if (pathPattern.matcher(k.getPath()).matches()) {
                        System.out.println(v);
                        final Map<String, Variant<?>> device1Properties = v.get("org.bluez.Device1");
                        if (null != device1Properties)
                        {
                            final Variant<DBusMap<String, Variant<byte[]>>> serviceData = (Variant<DBusMap<String, Variant<byte[]>>>)device1Properties.get("ServiceData");
                            final byte[] bytes = serviceData.getValue().get(serviceDataKey).getValue();
                            printInterpreted(bytes);
                        }
                    }
                }
            );

            final Adapter1 adapter1 = cn.getRemoteObject("org.bluez", "/org/bluez/hci0", Adapter1.class);

            Map<String, Variant<?>> filter = new HashMap<>();

            filter.put("Transport", new Variant<>("le"));
            adapter1.SetDiscoveryFilter(filter);
            adapter1.StartDiscovery();

            Thread.sleep(10_000);

            adapter1.StopDiscovery();
        }
    }

    static void printInterpreted(final byte[] value1)
    {
        System.out.println("Values from: " + Arrays.toString(value1));

        if (7 < value1.length)
        {
            int hi_byte = value1[7] & 0xFF;
            int lo_byte = value1[6] & 0xFF;
            float v = (float)(hi_byte << 8 | lo_byte) / 100.0f;
            System.out.printf("Temp: %.2f%n", v);
        }

        if (9 < value1.length)
        {
            int hi_byte = value1[9] & 0xFF;
            int lo_byte = value1[8] & 0xFF;
            float v = (float)(hi_byte << 8 | lo_byte) / 100.0f;
            System.out.printf("Humidity: %.2f%n", v);
        }

        if (12 < value1.length)
        {
            int v = value1[12] & 0xFF;
            System.out.println("Battery: " + v);
        }
    }
}

package com.skt_wifitimer.wifi_timer;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleManager {
    private static final String TAG = "BleManager";
    @SuppressWarnings("StaticFieldLeak")
    private static BleManager instance;    //class variable , a reference to an object
                                           //static variable.
    private BluetoothGatt bluetoothGatt;
    private final List<BleListener> listeners = new ArrayList<>();
    private Context context;


    //Implementing publisher-subscriber pattern.
    public interface BleListener {
        void onConnectionStateChange(int newState);
        void onServicesDiscovered();
    }

    private BleManager() {}           //a private constructor.

    public static synchronized BleManager getInstance() {    //static function
        if (instance == null) {
            instance = new BleManager();
        }
        return instance;
    }

    //method to register the suscribers like mainactivity and all.
    public void addListener(BleListener listener) {
        if (!listeners.contains(listener)) { //check for duplicates
            listeners.add(listener);
        }
    }

    public void removeListener(BleListener listener) {
        listeners.remove(listener);
    }

    //this is like a getter method.
    public BluetoothGatt getGatt() {
        return bluetoothGatt;
    }

    //
    public void connect(Context context, BluetoothDevice device) {
        this.context = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
            return;
        }

        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        //does connection and returns a bluetooth gatt object.
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.disconnect();
        }
    }

    public void writeCharacteristic(UUID serviceUuid, UUID charUuid, byte[] data) {
        writeCharacteristic(serviceUuid, charUuid, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
    }

    //This is a wrapper function for writing characteristics to the esp32 device.

    public void writeCharacteristic(UUID serviceUuid, UUID charUuid, byte[] data, int writeType) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "Write failed: bluetoothGatt is null");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Write failed: BLUETOOTH_CONNECT permission not granted");
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
        if (service == null) {
            Log.e(TAG, "Write failed: Service not found: " + serviceUuid);
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
        if (characteristic == null) {
            Log.e(TAG, "Write failed: Characteristic not found: " + charUuid);
            return;
        }

        Log.d(TAG, "Writing to characteristic: " + charUuid + " data: " + bytesToHex(data) + " type: " + writeType);

        boolean success;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int result = bluetoothGatt.writeCharacteristic(characteristic, data, writeType);
            success = (result == BluetoothGatt.GATT_SUCCESS);
        } else {
            characteristic.setValue(data);
            characteristic.setWriteType(writeType);
            success = bluetoothGatt.writeCharacteristic(characteristic);
        }
        
        if (!success) {
            Log.e(TAG, "Write operation failed to start");
        }
    }

    //This method is just a helper for debugging BLE data.
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    //this is an anonymous inner class.

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null &&
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    gatt.discoverServices();
                }
            }

            //notify the listeners .Tell all activities the connections changed.
            for (BleListener listener : new ArrayList<>(listeners)) {
                listener.onConnectionStateChange(newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully");
                for (BleListener listener : new ArrayList<>(listeners)) {
                    listener.onServicesDiscovered();
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write SUCCESS: " + characteristic.getUuid());
            } else {
                Log.e(TAG, "Write FAILED: " + characteristic.getUuid() + " status: " + status);
            }
        }
    };
}

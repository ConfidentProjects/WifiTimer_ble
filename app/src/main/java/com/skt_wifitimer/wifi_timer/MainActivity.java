package com.skt_wifitimer.wifi_timer;

//Ble related
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements BleManager.BleListener {

    private BluetoothAdapter bluetoothAdapter;
    private boolean scanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DeviceAdapter adapter;
    private final ArrayList<BluetoothDeviceModel> deviceList = new ArrayList<>();
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private BleManager bleManager;


    // Model class for Bluetooth devices
    static class BluetoothDeviceModel {
        String name;
        String address;
        String status;
        BluetoothDevice device;

        //Constructor.
        BluetoothDeviceModel(String name, String address, String status, BluetoothDevice device) {
            this.name = name;
            this.address = address;
            this.status = status;
            this.device = device;
        }


        /*
         *    Two BluetoothDeviceModel objects are considered equal
`````````*`````if their MAC addresses are equal.
         */

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BluetoothDeviceModel that = (BluetoothDeviceModel) o;
            return address.equals(that.address);
        }

        //hashcode is used to jump to the object location quickly
        @Override
        public int hashCode() {
            return address.hashCode();
        }
    }

    /*       Custom Adapter for the ListView which converts bluetooth device data on the screen to
     *      visual rows on the screen
     */
    class DeviceAdapter extends ArrayAdapter<BluetoothDeviceModel> {

        //constructor over here.
        public DeviceAdapter(MainActivity context, ArrayList<BluetoothDeviceModel> devices) {
            super(context, 0, devices);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.device_list_item, parent, false);
            }

            BluetoothDeviceModel device = getItem(position);

            //ConvertView is for reusing the old rows.

            TextView nameView = convertView.findViewById(R.id.deviceName);
            TextView addressView = convertView.findViewById(R.id.deviceAddress);
            TextView statusView = convertView.findViewById(R.id.connectionStatus);
            Button connectBtn = convertView.findViewById(R.id.connectButton);

            if (device != null) {
                nameView.setText(device.name != null ? device.name : "Unknown Device");
                addressView.setText(device.address);
                statusView.setText(device.status);

                // Change status color
                if ("Connected".equals(device.status)) {
                    statusView.setTextColor(ContextCompat.getColor(getContext(), android.R.color.holo_green_dark));
                } else {
                    statusView.setTextColor(ContextCompat.getColor(getContext(), android.R.color.holo_red_dark));
                }

                // Navigate to settings ONLY if the device is connected
                View.OnClickListener openSettings = v -> {
                    if ("Connected".equals(device.status)) {
                        Intent intent = new Intent(getContext(), SettingPageActivity.class);
                        intent.putExtra("device_address", device.address);
                        getContext().startActivity(intent);
                    } else {
                        Toast.makeText(getContext(), "Please connect first", Toast.LENGTH_SHORT).show();
                    }
                };
                nameView.setOnClickListener(openSettings);
                addressView.setOnClickListener(openSettings);

                connectBtn.setOnClickListener(v -> {
                    stopScan(); // Stop scanning when connecting
                   // Toast.makeText(getContext(), "Connecting to " + device.name, Toast.LENGTH_SHORT).show();
                    bleManager.connect(MainActivity.this, device.device);
                });
            }

            return convertView;
        }
    }

    @Override
    public void onConnectionStateChange(int newState) {
        // Find the device currently being connected - this is a simplification
        // In a real app you might want to track which device you are connecting to
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            runOnUiThread(() -> {
             //  Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                // We update all models that might be connecting for now, or just the one with matching address
                // Since BleManager doesn't give us the address here easily without more state,
                // we can either update BleManager to pass the address or find the gatt device
                BluetoothGatt gatt = bleManager.getGatt();
                if (gatt != null) {
                    updateDeviceStatus(gatt.getDevice().getAddress(), "Connected");
                }
            });
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            runOnUiThread(() -> {
              //  Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                BluetoothGatt gatt = bleManager.getGatt();
                if (gatt != null) {
                    updateDeviceStatus(gatt.getDevice().getAddress(), "Disconnected");
                }
            });
        }
    }

    @Override
    public void onServicesDiscovered() {
       // runOnUiThread(() -> Toast.makeText(MainActivity.this, "Services Discovered Successfully", Toast.LENGTH_SHORT).show());
    }

    private void updateDeviceStatus(String address, String status) {
        for (BluetoothDeviceModel model : deviceList) {
            if (model.address.equals(address)) {
                model.status = status;
                runOnUiThread(() -> adapter.notifyDataSetChanged());
                break;
            }
        }
    }


    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();

            String deviceName;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    deviceName = device.getName();
                } else {
                    deviceName = null;
                }
            } else {
                deviceName = device.getName();
            }

            BluetoothDeviceModel deviceModel = new BluetoothDeviceModel(deviceName, device.getAddress(), "Not Connected", device);

            boolean found = false;
            for (BluetoothDeviceModel model : deviceList) {
                if (model.address.equals(device.getAddress())) {
                    // Device already in list
                    if (!"Connected".equals(model.status)) {
                        model.status = "Not Connected"; // Found again, so it's available
                        if (deviceName != null) model.name = deviceName;
                    }
                    found = true;
                    break;
                }
            }

            if (!found) {
                deviceList.add(deviceModel);
            }
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        //sets up the object for bluetooth connections....
        bleManager = BleManager.getInstance();
        bleManager.addListener(this);

        ListView listView = findViewById(R.id.deviceList);
        adapter = new DeviceAdapter(this, deviceList);
        listView.setAdapter(adapter);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
        setupScanButton();
    }

    private void setupScanButton() {
        findViewById(R.id.scanButton).setOnClickListener(v -> checkPermissionsAndStartScan());
    }

    private void checkPermissionsAndStartScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }
        startScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            if (grantResults.length == 0) {
                allGranted = false;
            } else {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            }
            
            if (allGranted) {
                startScan();
            } else {
                Toast.makeText(this, "Permissions required for scanning", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleManager.removeListener(this);
    }

    //starts the scanning process
    private void startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "BLE not supported or Bluetooth off", Toast.LENGTH_SHORT).show();
            return;
        }

        if (scanning) return;

        // Keep connected devices in the list, remove others
        ArrayList<BluetoothDeviceModel> toRemove = new ArrayList<>();
        for (BluetoothDeviceModel model : deviceList) {
            if (!"Connected".equals(model.status)) {
                toRemove.add(model);
            }
        }
        deviceList.removeAll(toRemove);
        adapter.notifyDataSetChanged();

        Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show();

        // Stops scanning after 10 seconds.
        final long SCAN_PERIOD = 10000;

        handler.postDelayed(this::stopScan, SCAN_PERIOD);

        scanning = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.startScan(leScanCallback);
            }
        } else {
            bluetoothLeScanner.startScan(leScanCallback);
        }
    }

    //Stops the scanning process
    private void stopScan() {
        if (!scanning) return;
        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            } else {
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        }
        scanning = false;
      //  Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show();
    }
}
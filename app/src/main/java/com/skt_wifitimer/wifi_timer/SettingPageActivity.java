package com.skt_wifitimer.wifi_timer;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.UUID;

public class SettingPageActivity extends AppCompatActivity implements BleManager.BleListener {

    private LinearLayout panelWifi, panelTimer;
    private TextView tabWifi, tabTimer;
    private Button applyButton;
    private EditText etWifiName, etWifiPassword, etPasscode, etDeviceName;
    private EditText etRunHour, etRunMin, etPauseHour, etPauseMin, etMonitorHour1, etMonitorMin1, etMonitorHour2, etMonitorMin2;

    private BleManager bleManager;

    // UUIDs for characteristics
    private final UUID PASSCODE_CHAR_UUID = UUID.fromString("01cdab89-6745-2301-8967-452301efcdab");
    private final UUID WIFI_SSID_CHAR_UUID = UUID.fromString("03cdab89-6745-2301-8967-452301efcdab");
    private final UUID WIFI_PASS_CHAR_UUID = UUID.fromString("04cdab89-6745-2301-8967-452301efcdab");
    private final UUID DEVICE_NAME_CHAR_UUID = UUID.fromString("05cdab89-6745-2301-8967-452301efcdab");

    private final UUID RUNTIME_CHAR_UUID = UUID.fromString("06cdab89-6745-2301-8967-452301efcdab");
    private final UUID PAUSETIME_CHAR_UUID = UUID.fromString("07cdab89-6745-2301-8967-452301efcdab");
    private final UUID MONITOR1_CHAR_UUID = UUID.fromString("08cdab89-6745-2301-8967-452301efcdab");
    private final UUID MONITOR2_CHAR_UUID = UUID.fromString("09cdab89-6745-2301-8967-452301efcdab");

    private final UUID WIFI_SERVICE_UUID = UUID.fromString("efcdab89-6745-2301-8967-452301efcdab");
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setting_page);
        
        // Handle window insets for full screen (but header should touch top)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        // Initialize Views
        panelWifi = findViewById(R.id.panelWifi);
        panelTimer = findViewById(R.id.panelTimer);
        tabWifi = findViewById(R.id.tabWifi);
        tabTimer = findViewById(R.id.tabTimer);
        applyButton = findViewById(R.id.applyButton);

        // WiFi Fields
        etWifiName = findViewById(R.id.etWifiName);
        etWifiPassword = findViewById(R.id.etWifiPassword);
        etPasscode = findViewById(R.id.etPasscode);
        etDeviceName = findViewById(R.id.etDeviceName);

        // Timer Fields
        etRunHour = findViewById(R.id.etRunHour);
        etRunMin = findViewById(R.id.etRunMin);
        etPauseHour = findViewById(R.id.etPauseHour);
        etPauseMin = findViewById(R.id.etPauseMin);
        etMonitorHour1 = findViewById(R.id.etMonitorHour1);
        etMonitorMin1 = findViewById(R.id.etMonitorMin1);
        etMonitorHour2 = findViewById(R.id.etMonitorHour2);
        etMonitorMin2 = findViewById(R.id.etMonitorMin2);

        // Apply filters to hours and minutes
        setupRangeFilters();

        bleManager = BleManager.getInstance();
        bleManager.addListener(this);

        // Tab selection logic
        tabWifi.setOnClickListener(v -> showPanel(true));
        tabTimer.setOnClickListener(v -> showPanel(false));
        
        // Apply button logic
        applyButton.setOnClickListener(v -> showConfirmationDialog());

        // Default view
        showPanel(true);
    }

    private void setupRangeFilters() {
        InputFilter hourFilter = new RangeFilter(0, 99);
        InputFilter minFilter = new RangeFilter(0, 59);

        etRunHour.setFilters(new InputFilter[]{hourFilter});
        etPauseHour.setFilters(new InputFilter[]{hourFilter});
        etMonitorHour1.setFilters(new InputFilter[]{hourFilter});
        etMonitorHour2.setFilters(new InputFilter[]{hourFilter});

        etRunMin.setFilters(new InputFilter[]{minFilter});
        etPauseMin.setFilters(new InputFilter[]{minFilter});
        etMonitorMin1.setFilters(new InputFilter[]{minFilter});
        etMonitorMin2.setFilters(new InputFilter[]{minFilter});
    }

    private static class RangeFilter implements InputFilter {
        private final int min, max;

        public RangeFilter(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            try {
                String newVal = dest.toString().substring(0, dstart) + source.toString().substring(start, end) + dest.toString().substring(dend);
                if (newVal.isEmpty()) return null;
                int input = Integer.parseInt(newVal);
                if (input >= min && input <= max) return null;
            } catch (NumberFormatException ignored) { }
            return "";
        }
    }

    private void showPanel(boolean isWifi) {
        panelWifi.setVisibility(isWifi ? View.VISIBLE : View.GONE);
        panelTimer.setVisibility(isWifi ? View.GONE : View.VISIBLE);

        tabWifi.setSelected(isWifi);
        tabTimer.setSelected(!isWifi);
    }

    private void showConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Apply Changes");
        builder.setMessage("Do you want to apply these changes?");
        
        builder.setPositiveButton("ALLOW", (dialog, which) -> {
            applyAllChanges();
            Toast.makeText(this, "Changes Applied", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("NOT ALLOW", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void applyAllChanges() {
        if (bleManager.getGatt() == null) {
            Toast.makeText(this, "Not connected to device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bleManager.getGatt().getServices().isEmpty()) {
            Toast.makeText(this, "Services not discovered yet. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        int delay = 0;
        final int STEP = 300; 
        final int WRITE_TYPE = android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

        if (panelWifi.getVisibility() == View.VISIBLE) {
            String passcode = etPasscode.getText().toString();
            String deviceName = etDeviceName.getText().toString();
            String wifiSSID = etWifiName.getText().toString();
            String wifiPass = etWifiPassword.getText().toString();

            Log.d("BLE_DEBUG", "Applying WiFi settings...");

            if (!passcode.isEmpty()) {
                handler.postDelayed(() -> {
                    Log.d("BLE_DEBUG", "Sending passcode...");
                    bleManager.writeCharacteristic(WIFI_SERVICE_UUID, PASSCODE_CHAR_UUID, passcode.getBytes(), WRITE_TYPE);
                }, delay);
                delay += STEP;
            }

            if (!deviceName.isEmpty()) {
                handler.postDelayed(() -> {
                    Log.d("BLE_DEBUG", "Sending device name...");
                    bleManager.writeCharacteristic(WIFI_SERVICE_UUID, DEVICE_NAME_CHAR_UUID, deviceName.getBytes(), WRITE_TYPE);
                }, delay);
                delay += STEP;
            }

            if (!wifiSSID.isEmpty()) {
                handler.postDelayed(() -> {
                    Log.d("BLE_DEBUG", "Sending WiFi SSID...");
                    bleManager.writeCharacteristic(WIFI_SERVICE_UUID, WIFI_SSID_CHAR_UUID, wifiSSID.getBytes(), WRITE_TYPE);
                }, delay);
                delay += STEP;
            }

            if (!wifiPass.isEmpty()) {
                handler.postDelayed(() -> {
                    Log.d("BLE_DEBUG", "Sending WiFi password...");
                    bleManager.writeCharacteristic(WIFI_SERVICE_UUID, WIFI_PASS_CHAR_UUID, wifiPass.getBytes(), WRITE_TYPE);
                }, delay);
            }
        } else if (panelTimer.getVisibility() == View.VISIBLE) {
            byte[] runBytes = timeToBytes(etRunHour, etRunMin);
            byte[] pauseBytes = timeToBytes(etPauseHour, etPauseMin);
            byte[] monitor1Bytes = timeToBytes(etMonitorHour1, etMonitorMin1);
            byte[] monitor2Bytes = timeToBytes(etMonitorHour2, etMonitorMin2);

            Log.d("BLE_DEBUG", "Applying Timer settings...");

            if (runBytes != null) {
                handler.postDelayed(() -> {
                    Log.d("BLE_DEBUG", "Sending Run Time...");
                    bleManager.writeCharacteristic(WIFI_SERVICE_UUID, RUNTIME_CHAR_UUID, runBytes, WRITE_TYPE);
                }, delay);
                delay += STEP;
            }

            if (pauseBytes != null) {
                handler.postDelayed(() -> {
                    Log.d("BLE_DEBUG", "Sending Pause Time...");
                    bleManager.writeCharacteristic(WIFI_SERVICE_UUID, PAUSETIME_CHAR_UUID, pauseBytes, WRITE_TYPE);
                }, delay);
                delay += STEP;
            }

            if (monitor1Bytes != null) {
                handler.postDelayed(() -> {
                    Log.d("BLE_DEBUG", "Sending Monitor 1...");
                    bleManager.writeCharacteristic(WIFI_SERVICE_UUID, MONITOR1_CHAR_UUID, monitor1Bytes, WRITE_TYPE);
                }, delay);
                delay += STEP;
            }

            if (monitor2Bytes != null) {
                handler.postDelayed(() -> {
                    Log.d("BLE_DEBUG", "Sending Monitor 2...");
                    bleManager.writeCharacteristic(WIFI_SERVICE_UUID, MONITOR2_CHAR_UUID, monitor2Bytes, WRITE_TYPE);
                }, delay);
            }
        }
    }

    private byte[] timeToBytes(EditText h, EditText m) {
        String hours = h.getText().toString();
        String mins = m.getText().toString();
        if (hours.isEmpty() && mins.isEmpty()) return null;
        
        int hh = hours.isEmpty() ? 0 : Integer.parseInt(hours);
        int mm = mins.isEmpty() ? 0 : Integer.parseInt(mins);
        
        byte[] data = new byte[2];
        data[0] = (byte) (hh & 0xFF); // First byte: Hours
        data[1] = (byte) (mm & 0xFF); // Second byte: Minutes
        return data;
    }

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    public void onConnectionStateChange(int newState) {
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Disconnected from device", Toast.LENGTH_SHORT).show();
                finish(); // Go back if connection lost
            });
        }
    }

    @Override
    public void onServicesDiscovered() {
        Log.d("BLE_DEBUG", "Services discovered on SettingPage");
        runOnUiThread(() -> Toast.makeText(this, "Device Ready", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleManager.removeListener(this);
    }
}

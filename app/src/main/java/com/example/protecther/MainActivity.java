package com.example.protecther;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private static final String TAG = "ProtectHer_Shield";
    private static final int PERM_CODE = 101;
    private static final int SMART_FOLLOW_PERM_CODE = 102;
    private static final int SMART_FOLLOW_SMS_PERM_CODE = 103;
    private static final int SMS_ACTION_PERMISSION_CODE = 104;
    private static final int VOICE_PERMISSION_CODE = 105;
    private static final int LOCATION_ACTION_PERMISSION_CODE = 106;
    private static final long SMART_FOLLOW_SCAN_INTERVAL_MS = 150_000L;
    private static final long SMART_FOLLOW_SCAN_DURATION_MS = 10_000L;

    private SensorManager sensorManager;
    private float acceleration, currentAccel, lastAccel;
    private int volClicks = 0;
    private long lastVolTime = 0, lastShakeTime = 0;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private FusedLocationProviderClient fusedLocationClient;
    private GeofencingClient geofencingClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    
    private double lat = 0, lng = 0;
    private boolean hasLocation = false;
    private boolean isGuardianModeActive = false;
    private boolean isLiveLocationSharing = false;
    private boolean servicesStopped;
    private static final long LIVE_LOCATION_SHARE_INTERVAL_MS = 30000L;

    private TextView tvStatus, tvDangerScore, tvDangerStatus, tvFollowStatus, tvFollowScore;
    private TextView tvFollowRadioCounts;
    private TextView tvFollowCountdown;
    private LinearProgressIndicator dangerProgress;
    private MaterialCardView micCard, dangerScoreCard, followMeCard;
    private com.google.android.material.materialswitch.MaterialSwitch smartFollowSwitch;
    private View riskStatusDot;
    private ImageView ivFollowIcon;

    private List<Contact> contacts;
    private SharedPreferences prefs;
    private final Gson gson = new Gson();

    private DangerScoreCalculator dangerCalculator = new DangerScoreCalculator();
    private FollowMeDetector followMeDetector = new FollowMeDetector();
    private RiskZoneRepository riskZoneRepository;
    private Handler intelligenceHandler = new Handler(Looper.getMainLooper());
    private Handler liveLocationShareHandler = new Handler(Looper.getMainLooper());
    private Runnable intelligenceTask;
    private Runnable liveLocationShareTask;
    private Runnable pendingSmsAction;
    private Runnable pendingLocationAction;
    private boolean smsPermissionRequestInFlight;
    private boolean locationPermissionRequestInFlight;
    private NetworkStateReceiver networkReceiver;
    private boolean smartFollowEnabled;
    private final SmartFollowAnalyzer smartFollowAnalyzer = new SmartFollowAnalyzer();
    private final Set<String> smartBleIds = ConcurrentHashMap.newKeySet();
    private final Set<String> smartWifiIds = ConcurrentHashMap.newKeySet();
    private final String smartFollowSessionSalt = UUID.randomUUID().toString();
    private BluetoothLeScanner bluetoothLeScanner;
    private WifiManager wifiManager;
    private ScanCallback smartFollowScanCallback;
    private BroadcastReceiver wifiScanReceiver;
    private boolean wifiReceiverRegistered;
    private boolean wifiScanPending;
    private boolean smartFollowScanning;
    private boolean smartFollowScanInProgress;
    private boolean smartFollowScreenVisible;
    private boolean smartFollowPermissionRequestInFlight;
    private boolean smartFollowHasCompletedScan;
    private boolean smartFollowAlertLatched;
    private boolean smartFollowSmsPermissionRequestInFlight;
    private String smartFollowAlertStatus = "";
    private int lastBleCount;
    private int lastWifiCount;
    private String smartFollowScanNote = "";
    private final Handler smartFollowHandler = new Handler(Looper.getMainLooper());
    private Runnable smartFollowCompleteRunnable;
    private Runnable nextSmartFollowScanRunnable;
    private boolean smartFollowReceiverRegistered;
    private SharedPreferences smartFollowStatePrefs;
    private int smartFollowScore;
    private int smartFollowNearbyCount;
    private int smartFollowNearbyBleCount;
    private int smartFollowNearbyWifiCount;
    private int smartFollowRepeatedCount;
    private int smartFollowMovingRepeatCount;
    private float smartFollowMovementMeters;
    private long smartFollowNextResultAtMillis;
    private boolean smartFollowPotential;
    private String smartFollowStatus = "Smart Follow detection is off.";

    private final BroadcastReceiver smartFollowUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getBooleanExtra("running", smartFollowEnabled)) {
                smartFollowEnabled = false;
                prefs.edit().putBoolean("smart_follow_enabled", false).apply();
                setSmartFollowSwitchChecked(false);
            }
            smartFollowScore = intent.getIntExtra("score", 0);
            smartFollowNearbyCount = intent.getIntExtra("nearby", 0);
            smartFollowNearbyBleCount = intent.getIntExtra("nearby_ble", 0);
            smartFollowNearbyWifiCount = intent.getIntExtra("nearby_wifi", 0);
            smartFollowRepeatedCount = intent.getIntExtra("repeated", 0);
            smartFollowMovingRepeatCount = intent.getIntExtra("moving", 0);
            smartFollowMovementMeters = intent.getFloatExtra("movement", 0f);
            smartFollowPotential = intent.getBooleanExtra("potential", false);
            smartFollowNextResultAtMillis = intent.getLongExtra(
                    "next_result_at", smartFollowNextResultAtMillis);
            smartFollowStatus = intent.getStringExtra("status");
            if (smartFollowStatus == null) smartFollowStatus = "Monitoring status unavailable.";
            smartFollowScanInProgress = smartFollowStatus.startsWith("Scanning nearby");
            if (smartFollowStatus.startsWith("Scan complete:")) {
                smartFollowHasCompletedScan = true;
            }
            refreshSmartFollowDisplay();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        prefs = getSharedPreferences("ProtectHer_Config_V10", MODE_PRIVATE);
        riskZoneRepository = new RiskZoneRepository(this);
        smartFollowStatePrefs = getSharedPreferences("ProtectHer_SmartFollow_State", MODE_PRIVATE);
        smartFollowEnabled = prefs.getBoolean("smart_follow_enabled", false);
        loadContacts();
        initUI();
        initCoreEngine();
        
        if (checkPermissions()) {
            startMonitoring();
            registerGeofences();
        }
        
        startIntelligenceEngine();
        setupRealTimeListeners();
    }

    private void setupRealTimeListeners() {
        if (servicesStopped || networkReceiver != null) return;
        networkReceiver = new NetworkStateReceiver(() -> updateRiskIntelligence());
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private void registerGeofences() {
        if (servicesStopped) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        List<Geofence> geofenceList = new ArrayList<>();
        
        // Add Safe Zone Example
        geofenceList.add(new Geofence.Builder()
                .setRequestId("SAFE_Home")
                .setCircularRegion(19.0760, 72.8777, 200)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build());

        // Add Risk Zone Examples from Repository
        for (RiskZone zone : riskZoneRepository.getRiskZones()) {
            geofenceList.add(new Geofence.Builder()
                    .setRequestId("RISK_" + zone.incidentType)
                    .setCircularRegion(zone.latitude, zone.longitude, zone.radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build());
        }

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofenceList)
                .build();

        geofencingClient.addGeofences(request, getGeofencePendingIntent())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofences Added"))
                .addOnFailureListener(e -> Log.e(TAG, "Geofence Error: " + e.getMessage()));
    }

    private PendingIntent getGeofencePendingIntent() {
        return PendingIntent.getBroadcast(this, 0,
                new Intent(this, GeofenceBroadcastReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private void startIntelligenceEngine() {
        if (servicesStopped) return;
        intelligenceTask = new Runnable() {
            @Override
            public void run() {
                if (servicesStopped) return;
                updateRiskIntelligence();
                intelligenceHandler.postDelayed(this, 5000);
            }
        };
        intelligenceHandler.post(intelligenceTask);
    }

    /**
     * Integration Hook for YOLO + ByteTrack
     * To use with real YOLO detections:
     * 1. Use CameraX to get ImageProxy frames.
     * 2. Pass frame to TFLite YOLO model.
     * 3. Convert YOLO Detections to DetectedPerson objects.
     * 4. Call followMeDetector.updateAndGetMaxSuspicion(list).
     */
    private void onObjectDetected(List<FollowMeDetector.DetectedPerson> detections) {
        float suspicion = followMeDetector.updateAndGetMaxSuspicion(detections);
        // Suspicion score logic is already wired in updateRiskIntelligence
    }

    private void initSmartFollowScanning() {
        prefs.edit().remove("smart_follow_map_points").apply();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        smartFollowScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (result.getDevice() != null && result.getDevice().getAddress() != null) {
                    smartBleIds.add(anonymousRadioId("ble", result.getDevice().getAddress()));
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                smartFollowHandler.post(() -> {
                    smartFollowScanNote = "BLE scan failed (" + errorCode + ").";
                    refreshSmartFollowDisplay();
                });
            }
        };

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!wifiScanPending || !smartFollowEnabled) return;
                wifiScanPending = false;
                if (!intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                    smartFollowScanNote = "Wi-Fi scan unavailable or throttled.";
                    return;
                }
                try {
                    List<android.net.wifi.ScanResult> results = wifiManager.getScanResults();
                    if (results == null) {
                        smartFollowScanNote = "Wi-Fi scan returned no result list.";
                        return;
                    }
                    for (android.net.wifi.ScanResult result : results) {
                        if (result.BSSID != null) smartWifiIds.add(anonymousRadioId("wifi", result.BSSID));
                    }
                    smartFollowScanNote = "";
                } catch (SecurityException e) {
                    smartFollowScanNote = "Wi-Fi scan permission is unavailable.";
                    Log.w(TAG, "Wi-Fi scan results access denied", e);
                }
            }
        };
        ContextCompat.registerReceiver(this, wifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        wifiReceiverRegistered = true;
        refreshSmartFollowDisplay();
    }

    private String anonymousRadioId(String radioType, String address) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((smartFollowSessionSalt + ":" + radioType + ":" + address)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) value.append('0');
                value.append(hex);
            }
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable for local radio ID hashing", e);
        }
    }

    private void setSmartFollowEnabled(boolean enabled) {
        smartFollowEnabled = enabled;
        SharedPreferences.Editor editor = prefs.edit().putBoolean("smart_follow_enabled", enabled);
        if (enabled) editor.putBoolean("smart_follow_sms_consent_asked", false);
        editor.apply();
        if (!enabled) {
            startSmartFollowService(SmartFollowMonitoringService.ACTION_STOP);
            stopSmartFollowScanning();
            smartFollowScanInProgress = false;
            smartFollowAnalyzer.reset();
            lastBleCount = 0;
            lastWifiCount = 0;
            smartFollowHasCompletedScan = false;
            smartFollowAlertLatched = false;
            smartFollowAlertStatus = "";
            smartFollowScanNote = "";
            smartFollowStatePrefs.edit().clear().apply();
            smartFollowScore = 0;
            smartFollowNearbyCount = 0;
            smartFollowNearbyBleCount = 0;
            smartFollowNearbyWifiCount = 0;
            smartFollowRepeatedCount = 0;
            smartFollowMovingRepeatCount = 0;
            smartFollowMovementMeters = 0f;
            smartFollowNextResultAtMillis = 0L;
            smartFollowPotential = false;
            smartFollowStatus = "Smart Follow monitoring is off.";
            refreshSmartFollowDisplay();
            return;
        }
        refreshSmartFollowDisplay();
        requestSmartFollowPermissionsOrScan();
    }

    private boolean hasSmartFollowPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmartFollowPermissionsOrScan() {
        if (!smartFollowEnabled || smartFollowPermissionRequestInFlight) return;
        if (!prefs.getBoolean("smart_follow_sms_consent_asked", false)) {
            showSmartFollowSmsConsent();
            return;
        }
        if (prefs.getBoolean("smart_follow_auto_sms", false)
                && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            smartFollowPermissionRequestInFlight = true;
            smartFollowSmsPermissionRequestInFlight = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, SMART_FOLLOW_SMS_PERM_CODE);
            return;
        }

        List<String> missingPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missingPermissions.isEmpty()) {
            smartFollowPermissionRequestInFlight = true;
            smartFollowScanNote = "Allow Location, Nearby devices and notifications for background monitoring.";
            refreshSmartFollowDisplay();
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]),
                    SMART_FOLLOW_PERM_CODE);
            return;
        }
        startSmartFollowService(SmartFollowMonitoringService.ACTION_START);
    }

    private void showSmartFollowSmsConsent() {
        if (smartFollowPermissionRequestInFlight) return;
        smartFollowPermissionRequestInFlight = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Optional automatic SOS")
                .setMessage("Smart Follow normally only shows an on-device alert. If you opt in, ProtectHer can also send an SMS to your trusted contacts only after a high heuristic threshold, repeated Bluetooth signals and substantial GPS movement; Wi-Fi observations may contribute to the estimate. Wi-Fi access points are often fixed infrastructure, and radio signals cannot confirm that anyone is following you. You can choose again next time you enable Smart Follow.")
                .setPositiveButton("Enable SMS alerts", (dialog, which) -> {
                    prefs.edit()
                            .putBoolean("smart_follow_sms_consent_asked", true)
                            .putBoolean("smart_follow_auto_sms", true)
                            .apply();
                    smartFollowPermissionRequestInFlight = false;
                    requestSmartFollowPermissionsOrScan();
                })
                .setNegativeButton("On-device alerts only", (dialog, which) -> {
                    prefs.edit()
                            .putBoolean("smart_follow_sms_consent_asked", true)
                            .putBoolean("smart_follow_auto_sms", false)
                            .apply();
                    smartFollowPermissionRequestInFlight = false;
                    requestSmartFollowPermissionsOrScan();
                })
                .setOnCancelListener(dialog -> {
                    smartFollowPermissionRequestInFlight = false;
                    smartFollowEnabled = false;
                    prefs.edit().putBoolean("smart_follow_enabled", false).apply();
                    setSmartFollowSwitchChecked(false);
                    refreshSmartFollowDisplay();
                })
                .show();
    }

    private void startSmartFollowService(String action) {
        Intent serviceIntent = new Intent(this, SmartFollowMonitoringService.class).setAction(action);
        try {
            if (SmartFollowMonitoringService.ACTION_START.equals(action)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent);
            } else {
                startService(serviceIntent);
            }
            if (SmartFollowMonitoringService.ACTION_START.equals(action)) {
                Toast.makeText(this, "Smart Follow background monitoring started",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalStateException | SecurityException error) {
            Log.e(TAG, "Unable to start Smart Follow monitoring service", error);
            smartFollowEnabled = false;
            prefs.edit().putBoolean("smart_follow_enabled", false).apply();
            setSmartFollowSwitchChecked(false);
            smartFollowScanNote = "Android could not start background monitoring: "
                    + error.getLocalizedMessage();
            refreshSmartFollowDisplay();
            Toast.makeText(this, smartFollowScanNote, Toast.LENGTH_LONG).show();
        }
    }

    private void setSmartFollowSwitchChecked(boolean checked) {
        if (smartFollowSwitch == null || smartFollowSwitch.isChecked() == checked) return;
        smartFollowSwitch.setOnCheckedChangeListener(null);
        smartFollowSwitch.setChecked(checked);
        smartFollowSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                setSmartFollowEnabled(isChecked));
    }

    private void startSmartFollowScanCycle() {
        if (!smartFollowEnabled || !smartFollowScreenVisible || smartFollowScanning) return;
        if (!hasSmartFollowPermissions()) {
            smartFollowScanNote = "Permission missing. Allow Location and Nearby devices in Android Settings, then enable scanning again.";
            refreshSmartFollowDisplay();
            return;
        }

        smartFollowScanning = true;
        smartBleIds.clear();
        smartWifiIds.clear();
        wifiScanPending = false;
        smartFollowScanNote = "";

        try {
            if (wifiManager == null || !wifiManager.isWifiEnabled()) {
                smartFollowScanNote = "Wi-Fi is off; scanning BLE only.";
            } else if (wifiManager.startScan()) {
                wifiScanPending = true;
            } else {
                smartFollowScanNote = "Wi-Fi scan unavailable or throttled.";
            }
        } catch (SecurityException e) {
            smartFollowScanNote = "Wi-Fi scan permission is unavailable.";
            Log.w(TAG, "Unable to start Wi-Fi scan", e);
        }

        try {
            BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            bluetoothLeScanner = adapter == null ? null : adapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                if (smartFollowScanNote.isEmpty()) smartFollowScanNote = "Bluetooth is off; scanning Wi-Fi only.";
            } else {
                bluetoothLeScanner.startScan(null,
                        new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                        smartFollowScanCallback);
            }
        } catch (SecurityException e) {
            smartFollowScanNote = "Bluetooth scan permission is unavailable.";
            Log.w(TAG, "Unable to start BLE scan", e);
        } catch (IllegalStateException e) {
            smartFollowScanNote = "Bluetooth scanning is currently unavailable.";
            Log.w(TAG, "BLE scanner is unavailable", e);
        }

        refreshSmartFollowDisplay();
        smartFollowCompleteRunnable = this::completeSmartFollowScanCycle;
        smartFollowHandler.postDelayed(smartFollowCompleteRunnable, SMART_FOLLOW_SCAN_DURATION_MS);
    }

    private void completeSmartFollowScanCycle() {
        if (!smartFollowScanning) return;
        smartFollowScanning = false;
        if (bluetoothLeScanner != null) {
            try {
                bluetoothLeScanner.stopScan(smartFollowScanCallback);
            } catch (SecurityException e) {
                Log.w(TAG, "Unable to stop BLE scan after permission change", e);
            }
        }
        if (wifiScanPending) {
            wifiScanPending = false;
            if (smartFollowScanNote.isEmpty()) smartFollowScanNote = "Wi-Fi scan did not return fresh results.";
        }

        Set<String> bleSnapshot = new HashSet<>(smartBleIds);
        Set<String> wifiSnapshot = new HashSet<>(smartWifiIds);
        lastBleCount = bleSnapshot.size();
        lastWifiCount = wifiSnapshot.size();
        long scanTimeMillis = System.currentTimeMillis();
        SmartFollowAnalyzer.AnalysisResult result = smartFollowAnalyzer.recordScan(
                bleSnapshot, wifiSnapshot, scanTimeMillis, hasLocation, lat, lng);
        smartFollowHasCompletedScan = true;
        updateSmartFollowEmergencyAlert(result);
        refreshSmartFollowDisplay();

        if (smartFollowEnabled && smartFollowScreenVisible) {
            nextSmartFollowScanRunnable = this::startSmartFollowScanCycle;
            smartFollowHandler.postDelayed(nextSmartFollowScanRunnable, SMART_FOLLOW_SCAN_INTERVAL_MS);
        }
    }

    private void stopSmartFollowScanning() {
        if (smartFollowCompleteRunnable != null) {
            smartFollowHandler.removeCallbacks(smartFollowCompleteRunnable);
        }
        if (nextSmartFollowScanRunnable != null) {
            smartFollowHandler.removeCallbacks(nextSmartFollowScanRunnable);
        }
        if (smartFollowScanning && bluetoothLeScanner != null) {
            try {
                bluetoothLeScanner.stopScan(smartFollowScanCallback);
            } catch (SecurityException e) {
                Log.w(TAG, "Unable to stop BLE scan", e);
            }
        }
        smartFollowScanning = false;
        wifiScanPending = false;
    }

    private void refreshSmartFollowDisplay() {
        long updatedAt = smartFollowStatePrefs.getLong("updated", 0L);
        if (updatedAt > 0L && System.currentTimeMillis() - updatedAt <= 5 * 60_000L) {
            smartFollowScore = smartFollowStatePrefs.getInt("score", smartFollowScore);
            smartFollowNearbyCount = smartFollowStatePrefs.getInt("nearby", smartFollowNearbyCount);
            smartFollowNearbyBleCount = smartFollowStatePrefs.getInt(
                    "nearby_ble", smartFollowNearbyBleCount);
            smartFollowNearbyWifiCount = smartFollowStatePrefs.getInt(
                    "nearby_wifi", smartFollowNearbyWifiCount);
            smartFollowRepeatedCount = smartFollowStatePrefs.getInt("repeated", smartFollowRepeatedCount);
            smartFollowMovingRepeatCount = smartFollowStatePrefs.getInt("moving", smartFollowMovingRepeatCount);
            smartFollowMovementMeters = smartFollowStatePrefs.getFloat("movement", smartFollowMovementMeters);
            smartFollowPotential = smartFollowStatePrefs.getBoolean("potential", smartFollowPotential);
            smartFollowStatus = smartFollowStatePrefs.getString("status", smartFollowStatus);
            smartFollowNextResultAtMillis = smartFollowStatePrefs.getLong(
                    "next_result_at", smartFollowNextResultAtMillis);
        }
        if (smartFollowScanInProgress) {
            tvFollowRadioCounts.setText("BLE: scanning…   Wi-Fi: scanning…");
        } else {
            tvFollowRadioCounts.setText(smartFollowHasCompletedScan
                    ? "BLE: " + smartFollowNearbyBleCount + "   Wi-Fi: " + smartFollowNearbyWifiCount
                    : "BLE: —   Wi-Fi: —");
        }
        refreshSmartFollowCountdown();
        if (!smartFollowEnabled) {
            tvFollowScore.setText("—");
            tvFollowStatus.setText("Smart Follow detection is off");
            tvFollowStatus.setTextColor(getColor(R.color.tech_text_secondary));
            ivFollowIcon.setColorFilter(getColor(R.color.tech_text_secondary));
            return;
        }

        tvFollowScore.setText(smartFollowScore + "%");
        if (smartFollowPotential) {
            String level = smartFollowScore >= 85 ? "High"
                    : smartFollowScore >= 70 ? "Elevated" : "Moderate";
            int color = smartFollowScore >= 85 ? getColor(R.color.tech_danger_red)
                    : getColor(R.color.tech_accent_blue);
            tvFollowStatus.setText(level + " potential indicators — not confirmation");
            tvFollowStatus.setTextColor(color);
            ivFollowIcon.setColorFilter(color);
        } else {
            if (!smartFollowScanNote.isEmpty()) {
                tvFollowStatus.setText("Smart Follow needs attention");
                tvFollowStatus.setTextColor(getColor(R.color.tech_accent_blue));
                ivFollowIcon.setColorFilter(getColor(R.color.tech_accent_blue));
            } else if (smartFollowEnabled) {
                tvFollowStatus.setText("Monitoring for repeated nearby signals");
                tvFollowStatus.setTextColor(getColor(R.color.tech_accent_cyan));
                ivFollowIcon.setColorFilter(getColor(R.color.tech_accent_cyan));
            }
        }

    }

    private void refreshSmartFollowCountdown() {
        smartFollowHandler.removeCallbacks(smartFollowCountdownRunnable);
        if (!smartFollowEnabled) {
            tvFollowCountdown.setText("Enable monitoring to start the result timer.");
            return;
        }

        long remainingMillis = smartFollowNextResultAtMillis - System.currentTimeMillis();
        if (smartFollowNextResultAtMillis <= 0L) {
            tvFollowCountdown.setText("Waiting for first result · Detection score "
                    + smartFollowScore + "%");
        } else if (remainingMillis <= 0L) {
            tvFollowCountdown.setText("Updating result · Detection score "
                    + smartFollowScore + "%");
        } else {
            long remainingSeconds = (remainingMillis + 999L) / 1_000L;
            long minutes = remainingSeconds / 60L;
            long seconds = remainingSeconds % 60L;
            tvFollowCountdown.setText(String.format(Locale.getDefault(),
                    "Next result in %02d:%02d · Detection score %d%%",
                    minutes, seconds, smartFollowScore));
        }
        if (smartFollowScreenVisible) {
            smartFollowHandler.postDelayed(smartFollowCountdownRunnable, 1_000L);
        }
    }

    private final Runnable smartFollowCountdownRunnable = this::refreshSmartFollowCountdown;

    private void updateSmartFollowEmergencyAlert(SmartFollowAnalyzer.AnalysisResult result) {
        boolean thresholdReached = SmartFollowAnalyzer.shouldTriggerEmergencyAlert(result);
        if (!thresholdReached) {
            smartFollowAlertLatched = false;
            smartFollowAlertStatus = "";
            return;
        }
        if (smartFollowAlertLatched) return;

        smartFollowAlertLatched = true;
        if (contacts == null || contacts.isEmpty()) {
            smartFollowAlertStatus = "Emergency SMS not sent: add a trusted contact first.";
            Toast.makeText(this, smartFollowAlertStatus, Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            smartFollowAlertStatus = "Emergency SMS needs SMS permission. Allow it in Android Settings.";
            if (!smartFollowSmsPermissionRequestInFlight) {
                smartFollowSmsPermissionRequestInFlight = true;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS}, SMART_FOLLOW_SMS_PERM_CODE);
            }
            return;
        }
        sendSmartFollowEmergencyAlert(result);
    }

    private void sendSmartFollowEmergencyAlert(SmartFollowAnalyzer.AnalysisResult result) {
        StringBuilder message = new StringBuilder()
                .append("ProtectHer emergency alert: Smart Follow estimate reached ")
                .append(result.suspicionScore).append("%. Repeated nearby signals: ")
                .append(result.repeatedBleCount).append(" Bluetooth, ")
                .append(result.repeatedWifiCount).append(" Wi-Fi; ")
                .append(result.movingRepeatCount).append(" reappeared after GPS movement. ")
                .append("This is an automated estimate, not confirmation.");
        if (hasLocation) {
            message.append(" Approx. location: ")
                    .append(String.format(Locale.US, "%.5f, %.5f", lat, lng));
        }

        int queuedCount = 0;
        int failedCount = 0;
        for (Contact contact : contacts) {
            if (contact == null || contact.phone == null || contact.phone.trim().isEmpty()) {
                failedCount++;
            } else if (sendDirectSMS(contact.phone.trim(), message.toString())) {
                queuedCount++;
            } else {
                failedCount++;
            }
        }
        if (queuedCount > 0) {
            smartFollowAlertStatus = "Emergency alert SMS queued for " + queuedCount
                    + " trusted contact(s)." + (failedCount > 0 ? " Some messages could not be queued." : "");
            Toast.makeText(this, "Smart Follow emergency alert queued", Toast.LENGTH_LONG).show();
        } else {
            smartFollowAlertStatus = "Emergency SMS could not be queued. Check SMS permission, SIM, and contact numbers.";
            Log.e(TAG, smartFollowAlertStatus);
            Toast.makeText(this, smartFollowAlertStatus, Toast.LENGTH_LONG).show();
        }
    }

    private void updateRiskIntelligence() {
        if (servicesStopped) return;
        Location currentLoc = null;
        if (hasLocation) {
            currentLoc = new Location("gps");
            currentLoc.setLatitude(lat);
            currentLoc.setLongitude(lng);
        }

        DangerScoreCalculator.DangerReport report = DangerScoreCalculator.calculate(
                this, currentLoc, acceleration, riskZoneRepository);

        runOnUiThread(() -> {
            if (servicesStopped) return;
            tvDangerScore.setText(report.score + "/100");
            dangerProgress.setProgress(report.score);
            tvDangerStatus.setText("Risk level: "
                    + report.level.name().toLowerCase(Locale.getDefault()));
            int color;
            switch (report.level) {
                case SAFE: color = getColor(R.color.tech_success_green); break;
                case MODERATE: color = getColor(R.color.tech_accent_cyan); break;
                case HIGH: color = getColor(R.color.tech_accent_blue); break;
                case CRITICAL: color = getColor(R.color.tech_danger_red); break;
                default: color = getColor(R.color.tech_text_secondary);
            }
            
            tvDangerScore.setTextColor(color);
            tvDangerStatus.setTextColor(color);
            dangerProgress.setIndicatorColor(color);
            if (riskStatusDot != null) {
                riskStatusDot.setBackgroundTintList(ColorStateList.valueOf(color));
                if (report.score > 60) riskStatusDot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse));
                else riskStatusDot.clearAnimation();
            }
            
            refreshSmartFollowDisplay();

            if (report.score > 80) {
                tvStatus.setText("High risk detected");
                tvStatus.setTextColor(getColor(R.color.tech_danger_red));
            }
        });
    }

    private void initUI() {
        tvStatus = findViewById(R.id.tvStatus);
        micCard = findViewById(R.id.micCard);
        
        tvDangerScore = findViewById(R.id.tvDangerScore);
        tvDangerStatus = findViewById(R.id.tvDangerStatus);
        dangerProgress = findViewById(R.id.dangerProgress);
        dangerScoreCard = findViewById(R.id.dangerScoreCard);
        
        tvFollowStatus = findViewById(R.id.tvFollowStatus);
        tvFollowScore = findViewById(R.id.tvFollowScore);
        followMeCard = findViewById(R.id.followMeCard);
        ivFollowIcon = findViewById(R.id.ivFollowIcon);
        riskStatusDot = findViewById(R.id.riskStatusDot);
        smartFollowSwitch = findViewById(R.id.smartFollowSwitch);
        tvFollowRadioCounts = findViewById(R.id.tvFollowRadioCounts);
        tvFollowCountdown = findViewById(R.id.tvFollowCountdown);
        smartFollowSwitch.setChecked(smartFollowEnabled);
        smartFollowSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                setSmartFollowEnabled(isChecked));
        findViewById(R.id.btnSOS).setOnClickListener(v -> triggerSOS("Manual Alert"));
        findViewById(R.id.btnStopSOS).setOnClickListener(v -> resetSystem());
        findViewById(R.id.btnStopAllServices).setOnClickListener(v -> {
            if (servicesStopped) resumeServices();
            else stopAllServices();
        });
        findViewById(R.id.btnSafeCheckIn).setOnClickListener(v -> performSafeCheckIn());
        findViewById(R.id.btnNearbyPlaces).setOnClickListener(v -> findNearbySafePlaces());
        findViewById(R.id.btnLiveLocationShare).setOnClickListener(v -> toggleLiveLocationSharing());
        findViewById(R.id.btnManageContacts).setOnClickListener(v -> openContactManager());
        
        View btnMic = findViewById(R.id.btnMic);
        if (btnMic != null) btnMic.setOnClickListener(v -> toggleGuardianMode());

    }

    private void initCoreEngine() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        currentAccel = SensorManager.GRAVITY_EARTH;
        lastAccel = SensorManager.GRAVITY_EARTH;
        acceleration = 0.00f;

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);

        // Fetch last known location immediately to avoid starting at 0,0
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    lat = location.getLatitude();
                    lng = location.getLongitude();
                    hasLocation = true;
                }
            });
        }

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updateInternalLocation(location);
                }
            }
        };

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            // Hear it properly: Enable partial results for faster detection
            speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechRecognizer.setRecognitionListener(new GuardianSpeechListener());
        }
    }

    private void toggleGuardianMode() {
        if (servicesStopped) return;
        if (!isGuardianModeActive
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, VOICE_PERMISSION_CODE);
            return;
        }
        if (!isGuardianModeActive && speechRecognizer == null) {
            Toast.makeText(this, "Speech recognition is unavailable on this device",
                    Toast.LENGTH_LONG).show();
            return;
        }
        isGuardianModeActive = !isGuardianModeActive;
        if (isGuardianModeActive) {
            tvStatus.setText("Guardian mode is listening");
            tvStatus.setTextColor(getColor(R.color.tech_accent_cyan));
            if (micCard != null) {
                micCard.setStrokeWidth(6);
                micCard.setStrokeColor(getColor(R.color.tech_accent_cyan));
            }
            startListening();
            Toast.makeText(this, "Voice Monitoring: ACTIVE", Toast.LENGTH_SHORT).show();
        } else {
            tvStatus.setText("ProtectHer is monitoring");
            tvStatus.setTextColor(getColor(R.color.tech_success_green));
            if (micCard != null) micCard.setStrokeWidth(0);
            if (speechRecognizer != null) speechRecognizer.stopListening();
            Toast.makeText(this, "Voice Monitoring: OFF", Toast.LENGTH_SHORT).show();
        }
    }

    private void triggerSOS(String source) {
        if (contacts == null || contacts.isEmpty()) {
            Toast.makeText(this, "Add a trusted contact before sending an SOS", Toast.LENGTH_LONG).show();
            return;
        }
        if (System.currentTimeMillis() - lastShakeTime < 5000) return;
        lastShakeTime = System.currentTimeMillis();

        runOnUiThread(() -> {
            tvStatus.setText("Emergency alert is active");
            tvStatus.setTextColor(getColor(R.color.tech_danger_red));
        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Priority 1: Get the most fresh and high-accuracy location
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        updateInternalLocation(task.getResult());
                    } else {
                        // Priority 2: Fallback to last known location if fresh fetch fails
                        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                            if (location != null) updateInternalLocation(location);
                            executeSOSBroadcast(source);
                        });
                        return; // Wait for lastLocation success
                    }
                    executeSOSBroadcast(source);
                });
        } else {
            executeSOSBroadcast(source);
        }
    }

    private void updateInternalLocation(Location location) {
        if (location != null) {
            lat = location.getLatitude();
            lng = location.getLongitude();
            hasLocation = true;
            Log.d(TAG, "Location Updated: " + lat + ", " + lng);
        }
    }

    private void executeSOSBroadcast(String source) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestSmsPermission(() -> executeSOSBroadcast(source));
            return;
        }
        StringBuilder alert = new StringBuilder("SOS EMERGENCY! Help needed. Trigger: ")
                .append(source).append('.');
        if (hasLocation) {
            alert.append(" Location: https://www.google.com/maps/search/?api=1&query=")
                    .append(lat).append(',').append(lng);
        } else {
            alert.append(" Location is unavailable.");
        }
        int sent = 0;
        for (Contact contact : contacts) {
            if (contact != null && contact.phone != null && sendDirectSMS(contact.phone, alert.toString())) {
                sent++;
            }
        }
        if (sent > 0) {
            Toast.makeText(this, "SOS sent to " + sent + " trusted contact(s)",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "SOS could not be sent. Check your SIM and contact numbers.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void performSafeCheckIn() {
        if (contacts == null || contacts.isEmpty()) {
            Toast.makeText(this, "Add a trusted contact before sending a safe check-in",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(this::performSafeCheckIn);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestSmsPermission(this::performSafeCheckIn);
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        updateInternalLocation(task.getResult());
                    } else {
                        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                            if (location != null) updateInternalLocation(location);
                            sendSafeMessage();
                        });
                        return;
                    }
                    sendSafeMessage();
                });
        }
    }

    private void toggleLiveLocationSharing() {
        if (servicesStopped) return;
        if (contacts == null || contacts.isEmpty()) {
            Toast.makeText(this, "Add trusted contacts before sharing your live location", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isLiveLocationSharing
                && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestSmsPermission(this::toggleLiveLocationSharing);
            return;
        }
        if (!isLiveLocationSharing
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(this::toggleLiveLocationSharing);
            return;
        }

        if (isLiveLocationSharing) {
            isLiveLocationSharing = false;
            if (liveLocationShareTask != null) {
                liveLocationShareHandler.removeCallbacks(liveLocationShareTask);
            }
            tvStatus.setText("ProtectHer is monitoring");
            tvStatus.setTextColor(getColor(R.color.tech_success_green));
            Toast.makeText(this, "Live location sharing stopped", Toast.LENGTH_SHORT).show();
            return;
        }

        isLiveLocationSharing = true;
        tvStatus.setText("Live location sharing is active");
        tvStatus.setTextColor(getColor(R.color.tech_accent_cyan));
        sendLiveLocationUpdate("Live location started");

        liveLocationShareTask = new Runnable() {
            @Override
            public void run() {
                if (!isLiveLocationSharing) return;
                sendLiveLocationUpdate("Periodic live location update");
                liveLocationShareHandler.postDelayed(this, LIVE_LOCATION_SHARE_INTERVAL_MS);
            }
        };
        liveLocationShareHandler.postDelayed(liveLocationShareTask, LIVE_LOCATION_SHARE_INTERVAL_MS);
        Toast.makeText(this, "Live location sharing started", Toast.LENGTH_SHORT).show();
    }

    private void sendLiveLocationUpdate(String statusLabel) {
        if (servicesStopped || !isLiveLocationSharing) return;
        if (contacts == null || contacts.isEmpty()) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnCompleteListener(task -> {
                        if (servicesStopped || !isLiveLocationSharing) return;
                        if (task.isSuccessful() && task.getResult() != null) {
                            updateInternalLocation(task.getResult());
                        } else {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                                if (servicesStopped || !isLiveLocationSharing) return;
                                if (location != null) {
                                    updateInternalLocation(location);
                                }
                                sendLocationShareMessage(statusLabel);
                            });
                            return;
                        }
                        sendLocationShareMessage(statusLabel);
                    });
        } else {
            sendLocationShareMessage(statusLabel);
        }
    }

    private void sendLocationShareMessage(String statusLabel) {
        if (servicesStopped || !isLiveLocationSharing) return;
        String map = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lng;
        String msg = "📡 " + statusLabel + ". Current location: " + map + "\nSafety note: Please check on me if I do not respond.";
        for (Contact c : contacts) sendDirectSMS(c.phone, msg);
        Log.d(TAG, "Sharing location update to trusted contacts");
    }

    private void sendSafeMessage() {
        String msg = hasLocation
                ? "I am SAFE at: https://www.google.com/maps/search/?api=1&query=" + lat + "," + lng
                : "I am SAFE. My current location is unavailable.";
        int sent = 0;
        for (Contact contact : contacts) {
            if (contact != null && contact.phone != null && sendDirectSMS(contact.phone, msg)) sent++;
        }
        Toast.makeText(this, sent > 0 ? "Safe check-in sent to " + sent + " contact(s)"
                        : "Safe check-in could not be sent. Check your SIM and contact numbers.",
                Toast.LENGTH_LONG).show();
    }

    private void findNearbySafePlaces() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission(this::findNearbySafePlaces);
            return;
        }
        String[] safeTypes = {"🚓 Police", "🏥 Hospital", "🛡️ Shelter", "🏪 24/7 Store"};
        String[] queries = {"police station", "hospital", "women's shelter", "24 hour convenience store"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("📍 SCAN SAFE ZONES")
                .setItems(safeTypes, (dialog, which) -> {
                    String query = queries[which];
                    if (hasLocation) query += " near " + lat + "," + lng;
                    Uri uri = Uri.parse("https://www.google.com/maps/search/?api=1&query="
                            + Uri.encode(query));
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
                    try {
                        startActivity(mapIntent);
                    } catch (ActivityNotFoundException error) {
                        Log.w(TAG, "No app can open a safe-place map search", error);
                        Toast.makeText(this, "No maps app is available to find nearby places",
                                Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void requestSmsPermission(Runnable action) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        if (smsPermissionRequestInFlight) return;
        pendingSmsAction = action;
        smsPermissionRequestInFlight = true;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.SEND_SMS}, SMS_ACTION_PERMISSION_CODE);
    }

    private void requestLocationPermission(Runnable action) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        if (locationPermissionRequestInFlight) return;
        pendingLocationAction = action;
        locationPermissionRequestInFlight = true;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_ACTION_PERMISSION_CODE);
    }

    private boolean sendDirectSMS(String phone, String msg) {
        try {
            SmsManager smsManager = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 
                ? getSystemService(SmsManager.class) : SmsManager.getDefault();
            if (smsManager == null) {
                Log.e(TAG, "SMS service is unavailable");
                return false;
            }
            ArrayList<String> parts = smsManager.divideMessage(msg);
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "SMS error: " + e.getMessage(), e);
            return false;
        }
    }

    private void stopAllServices() {
        if (servicesStopped) return;
        servicesStopped = true;

        sensorManager.unregisterListener(this);
        fusedLocationClient.removeLocationUpdates(locationCallback);
        geofencingClient.removeGeofences(getGeofencePendingIntent())
                .addOnSuccessListener(unused -> Log.d(TAG, "Geofences Removed"))
                .addOnFailureListener(e -> Log.e(TAG, "Unable to remove geofences", e));

        if (intelligenceTask != null) {
            intelligenceHandler.removeCallbacks(intelligenceTask);
            intelligenceTask = null;
        }
        if (liveLocationShareTask != null) {
            liveLocationShareHandler.removeCallbacks(liveLocationShareTask);
            liveLocationShareTask = null;
        }
        isLiveLocationSharing = false;

        isGuardianModeActive = false;
        if (speechRecognizer != null) speechRecognizer.cancel();
        if (micCard != null) micCard.setStrokeWidth(0);
        if (riskStatusDot != null) riskStatusDot.clearAnimation();

        setSmartFollowEnabled(false);
        if (smartFollowSwitch.isChecked()) smartFollowSwitch.setChecked(false);

        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
        if (wifiReceiverRegistered) {
            unregisterReceiver(wifiScanReceiver);
            wifiReceiverRegistered = false;
        }

        tvStatus.setText("All safety monitoring stopped");
        tvStatus.setTextColor(getColor(R.color.tech_text_secondary));
        com.google.android.material.button.MaterialButton stopResumeButton =
                findViewById(R.id.btnStopAllServices);
        stopResumeButton.setText("Resume Services");
        stopResumeButton.setTextColor(getColor(R.color.tech_accent_cyan));
        stopResumeButton.setStrokeColorResource(R.color.tech_accent_cyan);
        Toast.makeText(this, "All ongoing safety services stopped", Toast.LENGTH_SHORT).show();
    }

    private void resumeServices() {
        servicesStopped = false;

        View btnMic = findViewById(R.id.btnMic);
        View btnLiveLocationShare = findViewById(R.id.btnLiveLocationShare);
        if (btnMic != null) btnMic.setEnabled(true);
        if (btnLiveLocationShare != null) btnLiveLocationShare.setEnabled(true);
        setSmartFollowSwitchChecked(false);

        com.google.android.material.button.MaterialButton stopResumeButton =
                findViewById(R.id.btnStopAllServices);
        stopResumeButton.setText("Stop All Services");
        stopResumeButton.setTextColor(getColor(R.color.tech_danger_red));
        stopResumeButton.setStrokeColorResource(R.color.tech_danger_red);

        if (checkPermissions()) {
            startMonitoring();
            registerGeofences();
        }
        startIntelligenceEngine();
        setupRealTimeListeners();
        tvStatus.setText("ProtectHer safety services are ready");
        tvStatus.setTextColor(getColor(R.color.tech_success_green));
        Toast.makeText(this, "Safety services resumed. Enable Smart Follow separately if needed.",
                Toast.LENGTH_LONG).show();
    }

    private void resetSystem() {
        if (servicesStopped) return;
        tvStatus.setText("ProtectHer is monitoring");
        tvStatus.setTextColor(getColor(R.color.tech_success_green));
        Toast.makeText(this, "Alert Deactivated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            long now = System.currentTimeMillis();
            if (now - lastVolTime < 1500) volClicks++; else volClicks = 1;
            lastVolTime = now;
            if (volClicks >= 3) { triggerSOS("Volume Key x3"); volClicks = 0; }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float x = event.values[0], y = event.values[1], z = event.values[2];
            acceleration = (float) Math.sqrt(x * x + y * y + z * z);
            if (acceleration > 15) triggerSOS("High Linear Accel Detect");
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Shake detection still uses regular accelerometer for compatibility
            float x = event.values[0], y = event.values[1], z = event.values[2];
            lastAccel = currentAccel;
            currentAccel = (float) Math.sqrt(x*x + y*y + z*z);
            float diff = Math.abs(currentAccel - lastAccel);
            if (diff > 15) triggerSOS("Shake Detect");
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    private void openContactManager() {
        View v = getLayoutInflater().inflate(R.layout.dialog_contacts, null);
        LinearLayout list = v.findViewById(R.id.contactsList);
        TextView contactCount = v.findViewById(R.id.tvContactCount);
        TextView emptyContacts = v.findViewById(R.id.tvEmptyContacts);
        list.removeAllViews();
        int count = contacts == null ? 0 : contacts.size();
        contactCount.setText(count + (count == 1 ? " contact added" : " contacts added"));
        emptyContacts.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        for (Contact c : contacts) {
            TextView t = new TextView(this);
            t.setText("👤 " + c.name + " (" + c.phone + ")");
            t.setPadding(20, 25, 20, 25);
            list.addView(t);
        }
        new MaterialAlertDialogBuilder(this).setTitle("TRUSTED NODES")
                .setView(v)
                .setPositiveButton("ADD NEW", (d, w) -> showAddNodeDialog())
                .setNegativeButton("WIPE", (d, w) -> { contacts.clear(); saveContacts(); })
                .show();
    }

    private void showAddNodeDialog() {
        EditText n = new EditText(this); n.setHint("Name");
        EditText p = new EditText(this); p.setHint("Phone");
        p.setInputType(InputType.TYPE_CLASS_PHONE);
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(60, 40, 60, 40); l.addView(n); l.addView(p);
        new MaterialAlertDialogBuilder(this).setTitle("REGISTER NODE").setView(l)
                .setPositiveButton("SAVE", (d, w) -> {
                    String name = n.getText().toString().trim();
                    String phone = p.getText().toString().trim();
                    if (!name.isEmpty() && !phone.isEmpty()) {
                        contacts.add(new Contact(name, phone));
                        saveContacts();
                    } else {
                        Toast.makeText(this, "Enter both a contact name and phone number",
                                Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void loadContacts() {
        String json = prefs.getString("node_data_v10", "");
        List<Contact> savedContacts = json.isEmpty() ? new ArrayList<>()
                : gson.fromJson(json, new TypeToken<List<Contact>>(){}.getType());
        contacts = savedContacts == null ? new ArrayList<>() : savedContacts;
    }

    private void saveContacts() { prefs.edit().putString("node_data_v10", gson.toJson(contacts)).apply(); }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) return true;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, PERM_CODE);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == PERM_CODE) {
            startMonitoring();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                registerGeofences();
            }
        } else if (rc == SMS_ACTION_PERMISSION_CODE) {
            smsPermissionRequestInFlight = false;
            Runnable action = pendingSmsAction;
            pendingSmsAction = null;
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                if (action != null) action.run();
            } else {
                Toast.makeText(this, "SMS permission is needed for this safety action",
                        Toast.LENGTH_LONG).show();
            }
        } else if (rc == VOICE_PERMISSION_CODE) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                toggleGuardianMode();
            } else {
                Toast.makeText(this, "Microphone permission is needed for Voice SOS",
                        Toast.LENGTH_LONG).show();
            }
        } else if (rc == LOCATION_ACTION_PERMISSION_CODE) {
            locationPermissionRequestInFlight = false;
            Runnable action = pendingLocationAction;
            pendingLocationAction = null;
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                registerGeofences();
                startMonitoring();
                if (action != null) action.run();
            } else {
                Toast.makeText(this, "Location permission is needed for this safety action",
                        Toast.LENGTH_LONG).show();
            }
        } else if (rc == SMART_FOLLOW_PERM_CODE) {
            smartFollowPermissionRequestInFlight = false;
            if (smartFollowEnabled && hasSmartFollowPermissions()) {
                smartFollowScanNote = "";
                startSmartFollowService(SmartFollowMonitoringService.ACTION_START);
            } else {
                smartFollowScanNote = "Monitoring needs precise location, Nearby devices and notification permissions. Allow them and turn Smart Follow on again.";
                smartFollowEnabled = false;
                prefs.edit().putBoolean("smart_follow_enabled", false).apply();
                setSmartFollowSwitchChecked(false);
                stopSmartFollowScanning();
                refreshSmartFollowDisplay();
                Toast.makeText(this, smartFollowScanNote,
                        Toast.LENGTH_LONG).show();
            }
        } else if (rc == SMART_FOLLOW_SMS_PERM_CODE) {
            smartFollowSmsPermissionRequestInFlight = false;
            boolean granted = g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED;
            smartFollowPermissionRequestInFlight = false;
            if (!granted) {
                prefs.edit().putBoolean("smart_follow_auto_sms", false).apply();
                Toast.makeText(this, "Automatic SMS alerts are off; on-device alerts remain enabled",
                        Toast.LENGTH_LONG).show();
            }
            if (smartFollowEnabled) requestSmartFollowPermissionsOrScan();
        }
    }

    private void startMonitoring() {
        if (servicesStopped) return;
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        
        if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        if (linear != null) sensorManager.registerListener(this, linear, SensorManager.SENSOR_DELAY_UI);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Check if GPS is enabled to avoid 0.0, 0.0
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Please enable GPS for accurate location", Toast.LENGTH_LONG).show();
            }

            locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                    .setMinUpdateIntervalMillis(2000)
                    .build();
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void startListening() {
        if (speechRecognizer != null && isGuardianModeActive) {
            try {
                speechRecognizer.startListening(speechIntent);
            } catch (RuntimeException e) {
                Log.e(TAG, "Unable to start Voice SOS recognition", e);
                Toast.makeText(this, "Voice SOS could not start. Check microphone access.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private class GuardianSpeechListener implements RecognitionListener {
        private void processSpeech(Bundle results) {
            if (results == null) return;
            ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (data != null) {
                for (String s : data) {
                    if (s.toLowerCase().contains("help")) { 
                        triggerSOS("Voice SOS"); 
                        break; 
                    }
                }
            }
        }

        @Override public void onResults(Bundle r) {
            processSpeech(r);
            if (isGuardianModeActive) restart();
        }

        @Override public void onPartialResults(Bundle p) {
            // Hear it properly even if the user hasn't finished speaking by checking partial results
            processSpeech(p);
        }

        @Override public void onError(int e) { if (isGuardianModeActive) restart(); }
        private void restart() { new Handler(Looper.getMainLooper()).postDelayed(() -> startListening(), 1000); }
        @Override public void onReadyForSpeech(Bundle p) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float r) {}
        @Override public void onBufferReceived(byte[] b) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onEvent(int t, Bundle p) {}
    }

    private static class Contact { public String name, phone; public Contact(String n, String p) { name = n; phone = p; } }

    @Override public void onLocationChanged(@NonNull Location location) { lat = location.getLatitude(); lng = location.getLongitude(); hasLocation = true; }
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}

    @Override protected void onResume() {
        super.onResume(); 
        smartFollowEnabled = prefs.getBoolean("smart_follow_enabled", false);
        setSmartFollowSwitchChecked(smartFollowEnabled);
        smartFollowScreenVisible = true;
        if (!smartFollowReceiverRegistered) {
            ContextCompat.registerReceiver(this, smartFollowUpdateReceiver,
                    new IntentFilter(SmartFollowMonitoringService.ACTION_UPDATE),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            smartFollowReceiverRegistered = true;
        }
        refreshSmartFollowDisplay();
        startMonitoring();
        if (smartFollowEnabled) requestSmartFollowPermissionsOrScan();
    }

    @Override protected void onPause() {
        smartFollowScreenVisible = false;
        smartFollowHandler.removeCallbacks(smartFollowCountdownRunnable);
        stopSmartFollowScanning();
        if (smartFollowReceiverRegistered) {
            unregisterReceiver(smartFollowUpdateReceiver);
            smartFollowReceiverRegistered = false;
        }
        super.onPause();
        sensorManager.unregisterListener(this);
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override protected void onDestroy() { 
        super.onDestroy(); 
        if (speechRecognizer != null) speechRecognizer.destroy(); 
        if (locationCallback != null) fusedLocationClient.removeLocationUpdates(locationCallback);
        if (intelligenceTask != null) intelligenceHandler.removeCallbacks(intelligenceTask);
        if (liveLocationShareTask != null) liveLocationShareHandler.removeCallbacks(liveLocationShareTask);
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
        if (smartFollowReceiverRegistered) {
            unregisterReceiver(smartFollowUpdateReceiver);
            smartFollowReceiverRegistered = false;
        }
        if (wifiReceiverRegistered) {
            unregisterReceiver(wifiScanReceiver);
            wifiReceiverRegistered = false;
        }
    }
}

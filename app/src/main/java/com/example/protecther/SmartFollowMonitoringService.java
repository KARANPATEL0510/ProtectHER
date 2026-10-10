package com.example.protecther;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.List;

public final class SmartFollowMonitoringService extends Service {
    public static final String ACTION_START = "com.example.protecther.SMART_FOLLOW_START";
    public static final String ACTION_STOP = "com.example.protecther.SMART_FOLLOW_STOP";
    public static final String ACTION_UPDATE = "com.example.protecther.SMART_FOLLOW_UPDATE";

    private static final String TAG = "ProtectHer_Follow";
    private static final String PREFERENCES = "ProtectHer_Config_V10";
    private static final String STATE_PREFERENCES = "ProtectHer_SmartFollow_State";
    private static final String MONITOR_CHANNEL_ID = "smart_follow_monitor";
    private static final String ALERT_CHANNEL_ID = "smart_follow_alerts";
    private static final int MONITOR_NOTIFICATION_ID = 4101;
    private static final int ALERT_NOTIFICATION_ID = 4102;
    private static final long LOCATION_INTERVAL_MILLIS = 30_000L;
    private static final long SCAN_INTERVAL_MILLIS = 60_000L;
    private static final long SCAN_DURATION_MILLIS = 8_000L;
    private static final long SCAN_START_INTERVAL_MILLIS =
            SCAN_INTERVAL_MILLIS - SCAN_DURATION_MILLIS;
    private static final int MIN_NEARBY_RSSI_DBM = -82;
    private static final long MAX_WIFI_RESULT_AGE_MILLIS = SCAN_INTERVAL_MILLIS;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
    private final Map<String, Integer> bleSignals = new HashMap<>();
    private final Map<String, Integer> wifiSignals = new HashMap<>();
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private BluetoothLeScanner bleScanner;
    private WifiManager wifiManager;
    private Runnable scanCompletion;
    private Runnable nextScan;
    private boolean scanning;
    private boolean wifiScanPending;
    private boolean alertLatched;
    private boolean smsAlertLatched;
    private boolean hasLocation;
    private double latitude;
    private double longitude;
    private long lastReliableLocationTime;
    private long nextResultAtMillis;
    private String bleScanNote = "";
    private String scanStatus = "Waiting for the first Bluetooth and Wi-Fi scan.";
    private String wifiScanNote = "";

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!scanning || !wifiScanPending) return;
            boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            collectWifiResults();
            wifiScanPending = false;
            if (wifiSignals.isEmpty()) {
                wifiScanNote = updated
                        ? "No nearby Wi-Fi access points met the proximity filter."
                        : "Wi-Fi scan was throttled; no recent results are available.";
            } else if (!updated) {
                wifiScanNote = "Wi-Fi scan was throttled; using recent cached results.";
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            recordBleResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) recordBleResult(result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "BLE scan failed with code " + errorCode);
            scanStatus = "Bluetooth scan failed (" + errorCode + "). Check Bluetooth and Nearby devices permission.";
            finishScan();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        ContextCompat.registerReceiver(this, wifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            return START_NOT_STICKY;
        }
        if (!getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean("smart_follow_enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startAsForegroundService();
        } catch (SecurityException | IllegalStateException error) {
            Log.e(TAG, "Android rejected the location foreground service", error);
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean("smart_follow_enabled", false).apply();
            publishState("Android blocked background monitoring. Check location permission and battery settings.",
                    analyzer.getLatest());
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!hasLocationPermission()) {
            publishState("Location permission was revoked. Smart Follow is off.", analyzer.getLatest());
            stopMonitoring();
            return START_NOT_STICKY;
        }

        requestLocationUpdates();
        startBleScanCycle();
        return START_STICKY;
    }

    private void startAsForegroundService() {
        Notification notification = buildMonitorNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(MONITOR_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(MONITOR_NOTIFICATION_ID, notification);
        }
    }

    private void requestLocationUpdates() {
        if (locationCallback != null || !hasLocationPermission()) return;
        int priority = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        LocationRequest request = new LocationRequest.Builder(priority, LOCATION_INTERVAL_MILLIS)
                .setMinUpdateIntervalMillis(15_000L)
                .setMaxUpdateDelayMillis(60_000L)
                .setWaitForAccurateLocation(false)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                for (Location location : result.getLocations()) recordLocation(location);
            }
        };
        try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                    .addOnFailureListener(error -> {
                        Log.e(TAG, "Location updates could not be started", error);
                        scanStatus = "Location updates failed: " + error.getLocalizedMessage();
                        publishState(scanStatus, analyzer.getLatest());
                    });
            locationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) recordLocation(location);
            }).addOnFailureListener(error -> Log.w(TAG, "Last known location is unavailable", error));
        } catch (SecurityException error) {
            Log.e(TAG, "Location permission changed while starting monitoring", error);
            scanStatus = "Location permission was revoked. Re-enable it to continue monitoring.";
            publishState(scanStatus, analyzer.getLatest());
            stopMonitoring();
        }
    }

    private void recordLocation(Location location) {
        if (location == null || !location.hasAccuracy() || location.getAccuracy() > 75.0f) return;
        if (System.currentTimeMillis() - location.getTime() > 120_000L) return;
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        lastReliableLocationTime = location.getTime();
        hasLocation = true;
    }

    private void startBleScanCycle() {
        if (scanning || !getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean("smart_follow_enabled", false)) return;
        bleSignals.clear();
        wifiSignals.clear();
        wifiScanNote = "";
        bleScanNote = "";
        wifiScanPending = false;
        scanning = true;
        if (nextResultAtMillis <= 0L) {
            nextResultAtMillis = System.currentTimeMillis() + SCAN_DURATION_MILLIS;
        }
        startWifiScan();
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        if (manager == null || manager.getAdapter() == null) {
            bleScanNote = "Bluetooth is unavailable; Wi-Fi scanning continues.";
        } else if (!hasBluetoothScanPermission()) {
            bleScanNote = "Bluetooth/Nearby devices permission is unavailable; Wi-Fi scanning continues.";
        } else {
            try {
                bleScanner = manager.getAdapter().getBluetoothLeScanner();
                if (bleScanner == null) {
                    bleScanNote = "Bluetooth is off; Wi-Fi scanning continues.";
                } else {
                    bleScanner.startScan(null,
                            new ScanSettings.Builder()
                                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                                    .setReportDelay(0L)
                                    .build(),
                            scanCallback);
                }
            } catch (SecurityException | IllegalStateException error) {
                Log.e(TAG, "Unable to start BLE scan", error);
                bleScanNote = "Bluetooth scan unavailable; Wi-Fi scanning continues.";
            }
        }

        scanStatus = "Scanning nearby Bluetooth and Wi-Fi signals; identifiers remain on this device.";
        publishState(scanStatus, analyzer.getLatest());
        scanCompletion = this::finishScan;
        handler.postDelayed(scanCompletion, SCAN_DURATION_MILLIS);
    }

    private void recordBleResult(ScanResult result) {
        if (result == null || result.getDevice() == null || result.getRssi() < MIN_NEARBY_RSSI_DBM) return;
        try {
            String address = result.getDevice().getAddress();
            if (address == null || address.isEmpty()
                    || "00:00:00:00:00:00".equals(address)
                    || "02:00:00:00:00:00".equals(address)) return;
            String hashedId = hashRadioAddress(address);
            Integer oldRssi = bleSignals.get(hashedId);
            if (oldRssi == null || result.getRssi() > oldRssi) {
                bleSignals.put(hashedId, result.getRssi());
            }
        } catch (SecurityException error) {
            Log.w(TAG, "BLE scan result could not be read after a permission change", error);
        }
    }

    private void startWifiScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            wifiScanNote = "Wi-Fi scan needs precise location permission.";
            return;
        }
        if (wifiManager == null) {
            wifiScanNote = "Wi-Fi is unavailable on this device.";
            return;
        }
        try {
            if (!wifiManager.isWifiEnabled()) {
                wifiScanNote = "Wi-Fi is off; Bluetooth scanning continues.";
                return;
            }
            wifiScanPending = true;
            if (!wifiManager.startScan()) {
                wifiScanPending = false;
                collectWifiResults();
                wifiScanNote = wifiSignals.isEmpty()
                        ? "Wi-Fi scan is throttled or unavailable; Bluetooth scanning continues."
                        : "Wi-Fi scan is throttled; using recent cached results.";
            }
        } catch (SecurityException | IllegalStateException error) {
            wifiScanPending = false;
            wifiScanNote = "Wi-Fi scan permission or service is unavailable.";
            Log.w(TAG, "Unable to start Wi-Fi scan", error);
        }
    }

    private void collectWifiResults() {
        if (wifiManager == null) return;
        try {
            List<android.net.wifi.ScanResult> results = wifiManager.getScanResults();
            if (results == null) return;
            long nowElapsedMillis = SystemClock.elapsedRealtime();
            for (android.net.wifi.ScanResult result : results) {
                if (result == null || result.BSSID == null || result.level < MIN_NEARBY_RSSI_DBM
                        || result.timestamp <= 0L) continue;
                long ageMillis = nowElapsedMillis - result.timestamp / 1_000L;
                if (ageMillis < 0L || ageMillis > MAX_WIFI_RESULT_AGE_MILLIS) continue;
                String bssid = result.BSSID;
                if (bssid.isEmpty() || "00:00:00:00:00:00".equals(bssid)
                        || "02:00:00:00:00:00".equals(bssid)) continue;

                String hashedId = hashRadioAddress("wifi:" + bssid);
                Integer previousRssi = wifiSignals.get(hashedId);
                if (previousRssi == null || result.level > previousRssi) {
                    wifiSignals.put(hashedId, result.level);
                }
            }
        } catch (SecurityException | IllegalStateException error) {
            wifiScanNote = "Wi-Fi scan results are unavailable; Bluetooth scanning continues.";
            Log.w(TAG, "Unable to read Wi-Fi scan results", error);
        }
    }

    private String hashRadioAddress(String address) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((getOrCreateRadioSalt() + ":" + address)
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder hashed = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                String hex = Integer.toHexString(value & 0xff);
                if (hex.length() == 1) hashed.append('0');
                hashed.append(hex);
            }
            return hashed.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable for local radio ID hashing", error);
        }
    }

    private String getOrCreateRadioSalt() {
        android.content.SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        String salt = preferences.getString("smart_follow_radio_salt", null);
        if (salt != null) return salt;
        salt = UUID.randomUUID().toString();
        preferences.edit().putString("smart_follow_radio_salt", salt).apply();
        return salt;
    }

    private void finishScan() {
        if (!scanning) return;
        scanning = false;
        if (scanCompletion != null) handler.removeCallbacks(scanCompletion);
        if (bleScanner != null) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (SecurityException | IllegalStateException error) {
                Log.w(TAG, "BLE scan could not be stopped cleanly", error);
            }
        }
        if (wifiScanPending) {
            wifiScanPending = false;
            collectWifiResults();
            if (wifiSignals.isEmpty()) {
                wifiScanNote = "Wi-Fi scan did not return recent results.";
            }
        }
        boolean locationFresh = hasLocation
                && System.currentTimeMillis() - lastReliableLocationTime <= 90_000L;
        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                new HashMap<>(bleSignals), new HashMap<>(wifiSignals), System.currentTimeMillis(),
                locationFresh, latitude, longitude);
        scanStatus = buildScanStatus(result, locationFresh);
        if (!wifiScanNote.isEmpty()) scanStatus += " " + wifiScanNote;
        if (!bleScanNote.isEmpty()) scanStatus += " " + bleScanNote;
        handleThreatResult(result);
        nextResultAtMillis = System.currentTimeMillis() + SCAN_INTERVAL_MILLIS;
        scheduleNextScan();
        publishState(scanStatus, result);
    }

    private String buildScanStatus(SmartFollowAnalyzer.AnalysisResult result, boolean locationFresh) {
        if (bleSignals.isEmpty() && wifiSignals.isEmpty()) {
            return "Scan complete: no nearby Bluetooth or Wi-Fi signals detected. "
                    + "Scanning continues; no signal detected does not mean the area is safe.";
        }
        if (result.hasRepeatEvidence) {
            return formatScanCounts(result) + ". Repeated signals were observed during movement. "
                    + "This is an uncertain indicator, not confirmation that anyone is following.";
        }
        if (result.repeatedBleCount + result.repeatedWifiCount > 0) {
            return formatScanCounts(result) + ". Repeated signals: BLE "
                    + result.repeatedBleCount + ", Wi-Fi " + result.repeatedWifiCount
                    + (locationFresh
                    ? "; up to " + Math.round(result.greatestMovementMeters)
                    + " m of movement correlated; the potential-indicator threshold is not established yet."
                    : "; movement could not be checked because the GPS fix is not fresh.");
        }
        if (!locationFresh) {
            return formatScanCounts(result) + ". No potential repeated-signal result yet; "
                    + "movement could not be checked because the GPS fix is not fresh. "
                    + "Check that Location is enabled and permission is allowed.";
        }
        return formatScanCounts(result) + ". No potential repeated-signal result yet; "
                + "checking for signals that persist as you move. This cannot confirm following.";
    }

    private String formatScanCounts(SmartFollowAnalyzer.AnalysisResult result) {
        return "Scan complete: BLE " + result.currentNearbyBleCount
                + ", Wi-Fi " + result.currentNearbyWifiCount + " nearby";
    }

    private void scheduleNextScan() {
        if (nextScan != null) handler.removeCallbacks(nextScan);
        if (getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean("smart_follow_enabled", false)) {
            nextScan = this::startBleScanCycle;
            handler.postDelayed(nextScan, SCAN_START_INTERVAL_MILLIS);
        }
    }

    private void handleThreatResult(SmartFollowAnalyzer.AnalysisResult result) {
        if (!result.hasRepeatEvidence) {
            if (result.suspicionScore < 35) {
                alertLatched = false;
                smsAlertLatched = false;
            }
            return;
        }

        if (!alertLatched && result.suspicionScore >= SmartFollowAnalyzer.POTENTIAL_THREAT_THRESHOLD) {
            alertLatched = true;
            notifyPotentialThreat(result);
        }

        boolean smsOptedIn = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean("smart_follow_auto_sms", false);
        if (!smsAlertLatched && smsOptedIn
                && SmartFollowAnalyzer.shouldTriggerEmergencyAlert(result)) {
            smsAlertLatched = true;
            sendOptionalEmergencySms(result);
        }
    }

    private void notifyPotentialThreat(SmartFollowAnalyzer.AnalysisResult result) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_security_shield)
                .setContentTitle("Potential repeated nearby signal")
                .setContentText(String.format(Locale.getDefault(),
                        "Risk estimate %d/100. This cannot confirm that anyone is following you.",
                        result.suspicionScore))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "ProtectHer observed repeated nearby Bluetooth/Wi-Fi signals across "
                                + result.movingRepeatCount + " movement-correlated observation(s). "
                                + "Wi-Fi access points are often fixed infrastructure. These signals can be unrelated, "
                                + "and this heuristic cannot identify a person or confirm following. "
                                + "If you feel unsafe, move to a staffed public place and use SOS if needed."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent())
                .build();
        manager.notify(ALERT_NOTIFICATION_ID, notification);
    }

    private void sendOptionalEmergencySms(SmartFollowAnalyzer.AnalysisResult result) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            publishState("Potential threat detected. Automatic SOS is on, but SMS permission is unavailable.",
                    result);
            return;
        }
        String contactsJson = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getString("node_data_v10", "");
        if (contactsJson == null || contactsJson.isEmpty()) {
            publishState("Potential threat detected. Automatic SOS was not sent: add a trusted contact.",
                    result);
            return;
        }

        StringBuilder message = new StringBuilder("ProtectHer: potential repeated nearby radio signals detected. ")
                .append("Risk estimate ").append(result.suspicionScore)
                .append("/100 after ")
                .append(result.repeatedBleCount).append(" Bluetooth and ")
                .append(result.repeatedWifiCount).append(" Wi-Fi signal(s) repeated during movement. ")
                .append("Wi-Fi access points are often fixed; this is not confirmation of following.");
        if (hasLocation && System.currentTimeMillis() - lastReliableLocationTime <= 90_000L) {
            message.append(" Approximate location: https://www.google.com/maps/search/?api=1&query=")
                    .append(latitude).append(',').append(longitude);
        }

        int queued = 0;
        try {
            JsonArray contacts = JsonParser.parseString(contactsJson).getAsJsonArray();
            for (JsonElement element : contacts) {
                if (!element.isJsonObject()) continue;
                JsonObject contact = element.getAsJsonObject();
                String phone = contact.has("phone") && !contact.get("phone").isJsonNull()
                        ? contact.get("phone").getAsString().trim() : "";
                if (phone.isEmpty()) continue;
                SmsManager smsManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? getSystemService(SmsManager.class) : SmsManager.getDefault();
                if (smsManager == null) continue;
                ArrayList<String> parts = smsManager.divideMessage(message.toString());
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
                queued++;
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Optional Smart Follow SMS could not be queued", error);
        }
        String status = queued > 0
                ? "Opt-in SOS SMS queued for " + queued + " trusted contact(s)."
                : "Potential threat detected. SOS SMS could not be queued; check SMS permission, SIM and contacts.";
        publishState(status, result);
        Log.i(TAG, status);
    }

    private void publishState(String status, SmartFollowAnalyzer.AnalysisResult result) {
        android.content.SharedPreferences state = getSharedPreferences(STATE_PREFERENCES, MODE_PRIVATE);
        state.edit()
                .putInt("score", result.suspicionScore)
                .putInt("nearby", result.currentNearbyCount)
                .putInt("nearby_ble", result.currentNearbyBleCount)
                .putInt("nearby_wifi", result.currentNearbyWifiCount)
                .putInt("repeated", result.repeatedBleCount + result.repeatedWifiCount)
                .putInt("repeated_ble", result.repeatedBleCount)
                .putInt("repeated_wifi", result.repeatedWifiCount)
                .putInt("moving", result.movingRepeatCount)
                .putLong("duration", result.repeatDurationMillis)
                .putFloat("movement", (float) result.greatestMovementMeters)
                .putBoolean("potential", result.hasRepeatEvidence)
                .putLong("next_result_at", nextResultAtMillis)
                .putLong("updated", System.currentTimeMillis())
                .putString("status", status)
                .apply();

        Intent update = new Intent(ACTION_UPDATE).setPackage(getPackageName());
        update.putExtra("score", result.suspicionScore);
        update.putExtra("nearby", result.currentNearbyCount);
        update.putExtra("nearby_ble", result.currentNearbyBleCount);
        update.putExtra("nearby_wifi", result.currentNearbyWifiCount);
        update.putExtra("repeated", result.repeatedBleCount + result.repeatedWifiCount);
        update.putExtra("repeated_ble", result.repeatedBleCount);
        update.putExtra("repeated_wifi", result.repeatedWifiCount);
        update.putExtra("moving", result.movingRepeatCount);
        update.putExtra("movement", (float) result.greatestMovementMeters);
        update.putExtra("potential", result.hasRepeatEvidence);
        update.putExtra("next_result_at", nextResultAtMillis);
        update.putExtra("running", getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean("smart_follow_enabled", false));
        update.putExtra("status", status);
        sendBroadcast(update);
    }

    private void createNotificationChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel monitor = new NotificationChannel(MONITOR_CHANNEL_ID,
                "Smart Follow monitoring", NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription("Visible while location and nearby Bluetooth/Wi-Fi Smart Follow monitoring is enabled.");
        NotificationChannel alerts = new NotificationChannel(ALERT_CHANNEL_ID,
                "Potential safety alerts", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Vibrating alerts for repeated nearby signals correlated with movement.");
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0L, 350L, 180L, 350L});
        manager.createNotificationChannel(monitor);
        manager.createNotificationChannel(alerts);
    }

    private Notification buildMonitorNotification() {
        Intent stopIntent = new Intent(this, SmartFollowMonitoringService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_security_shield)
                .setContentTitle("ProtectHer Smart Follow is active")
                .setContentText("Periodic GPS, Bluetooth and Wi-Fi checks. Radio IDs and movement traces stay on this device.")
                .setContentIntent(openAppPendingIntent())
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
                .build();
    }

    private PendingIntent openAppPendingIntent() {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBluetoothScanPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED);
    }

    private void stopWifiScanReceiver() {
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException error) {
            Log.w(TAG, "Wi-Fi scan receiver was already unregistered", error);
        }
    }

    private void stopMonitoring() {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean("smart_follow_enabled", false).apply();
        if (scanCompletion != null) handler.removeCallbacks(scanCompletion);
        if (nextScan != null) handler.removeCallbacks(nextScan);
        if (scanning && bleScanner != null) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (SecurityException | IllegalStateException error) {
                Log.w(TAG, "BLE scan was already stopped or permission was revoked", error);
            }
        }
        scanning = false;
        wifiScanPending = false;
        if (locationCallback != null) {
            locationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
        analyzer.reset();
        nextResultAtMillis = 0L;
        alertLatched = false;
        smsAlertLatched = false;
        scanStatus = "Smart Follow monitoring is off.";
        publishState(scanStatus, analyzer.getLatest());
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (scanCompletion != null) handler.removeCallbacks(scanCompletion);
        if (nextScan != null) handler.removeCallbacks(nextScan);
        if (scanning && bleScanner != null) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (SecurityException | IllegalStateException error) {
                Log.w(TAG, "Unable to stop BLE scan during service shutdown", error);
            }
        }
        scanning = false;
        wifiScanPending = false;
        if (locationCallback != null && locationClient != null) {
            locationClient.removeLocationUpdates(locationCallback);
        }
        stopWifiScanReceiver();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

package com.example.protecther;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;

import java.util.Calendar;

public class DangerScoreCalculator {

    public enum RiskLevel {
        SAFE, MODERATE, HIGH, CRITICAL
    }

    public static class DangerReport {
        public int score;
        public RiskLevel level;
        public String reason;
        
        // Detailed metrics for UI display
        public int locationRisk, timeRisk, movementRisk, batteryRisk, networkRisk;

        public DangerReport(int score, RiskLevel level, String reason) {
            this.score = score;
            this.level = level;
            this.reason = reason;
        }
    }

    public static DangerReport calculate(Context context, Location currentLoc, float linearAcceleration, RiskZoneRepository repository) {
        StringBuilder reasons = new StringBuilder();

        // 1. Location Risk (30%) - Dynamic Risk Zones
        int locRisk = 0;
        if (repository != null && currentLoc != null) {
            locRisk = repository.calculateLocationRisk(currentLoc);
            if (locRisk > 70) reasons.append("High-Risk Area | ");
            else if (locRisk > 30) reasons.append("Caution Zone | ");
        } else if (currentLoc == null) {
            locRisk = 50; // Moderate risk if location is unknown
            reasons.append("Location Unknown | ");
        }

        // 2. Time Risk (20%) - Fixed Time Ranges
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int timeRisk;
        if (hour >= 22 || hour < 5) {
            timeRisk = 100; // 22:00 - 04:59 (High)
            reasons.append("High-Risk Time | ");
        } else if (hour >= 18 || (hour >= 5 && hour < 7)) {
            timeRisk = 60; // 18:00 - 21:59 and 05:00 - 06:59 (Moderate)
            reasons.append("Moderate-Risk Time | ");
        } else {
            timeRisk = 10; // Daytime
        }

        // 3. Movement Risk (20%) - Linear Acceleration (Gravity Removed)
        // linearAcceleration passed in is already gravity-removed magnitude
        // Normalize: 0 to 15 m/s^2 maps to 0-100
        int moveRisk = (int) Math.min(100, (linearAcceleration / 15f) * 100);
        if (moveRisk > 60) reasons.append("Unusual Movement | ");

        // 4. Battery Risk (15%) - Normalized Inversely
        int battRisk = 0;
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float pct = (level / (float) scale) * 100;
            
            if (pct < 20) {
                battRisk = 100;
                reasons.append("Critical Battery | ");
            } else if (pct < 50) {
                battRisk = 60;
                reasons.append("Low Battery | ");
            } else if (pct < 80) {
                battRisk = 30;
            } else {
                battRisk = 0;
            }
        }

        // 5. Network Risk (15%) - Emergency Communication Availability
        int netRisk = 0;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) {
                netRisk = 100; // No active network
                reasons.append("No Signal | ");
            } else if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                netRisk = 80; // Network exists but no internet
                reasons.append("No Data | ");
            } else {
                netRisk = 0; // Internet available
            }
        }

        // Weighted Calculation
        double finalScore = (locRisk * 0.30) + 
                            (timeRisk * 0.20) + 
                            (moveRisk * 0.20) + 
                            (battRisk * 0.15) + 
                            (netRisk * 0.15);

        int score = (int) Math.round(finalScore);
        
        RiskLevel level;
        if (score <= 30) level = RiskLevel.SAFE;
        else if (score <= 60) level = RiskLevel.MODERATE;
        else if (score <= 80) level = RiskLevel.HIGH;
        else level = RiskLevel.CRITICAL;

        String reasonStr = reasons.toString();
        if (reasonStr.endsWith(" | ")) reasonStr = reasonStr.substring(0, reasonStr.length() - 3);
        if (reasonStr.isEmpty()) reasonStr = "Optimal Conditions";

        DangerReport report = new DangerReport(score, level, reasonStr);
        report.locationRisk = locRisk;
        report.timeRisk = timeRisk;
        report.movementRisk = moveRisk;
        report.batteryRisk = battRisk;
        report.networkRisk = netRisk;
        
        return report;
    }
}

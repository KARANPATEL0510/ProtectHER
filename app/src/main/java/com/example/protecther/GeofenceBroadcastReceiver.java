package com.example.protecther;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            return;
        }

        int transitionType = geofencingEvent.getGeofenceTransition();
        for (Geofence geofence : geofencingEvent.getTriggeringGeofences()) {
            String id = geofence.getRequestId();
            
            if (id.startsWith("SAFE_")) {
                handleSafeZone(context, transitionType, id.substring(5));
            } else if (id.startsWith("RISK_")) {
                handleRiskZone(context, transitionType, id.substring(5));
            }
        }
    }

    private void handleSafeZone(Context context, int transition, String name) {
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Toast.makeText(context, "Shield Active: Entered Safe Zone (" + name + ")", Toast.LENGTH_SHORT).show();
            updateSafeStatus(context, true);
        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Toast.makeText(context, "Exited Safe Zone: Monitoring Intensified", Toast.LENGTH_LONG).show();
            updateSafeStatus(context, false);
        }
    }

    private void handleRiskZone(Context context, int transition, String type) {
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Toast.makeText(context, "🚨 WARNING: Entered High-Risk Zone (" + type + ")!", Toast.LENGTH_LONG).show();
        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Toast.makeText(context, "Safe: Exited Risk Zone", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSafeStatus(Context context, boolean isInside) {
        context.getSharedPreferences("ProtectHer_Config_V10", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_inside_safe_zone", isInside)
                .apply();
    }
}

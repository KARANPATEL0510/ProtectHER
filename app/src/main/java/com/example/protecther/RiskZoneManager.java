package com.example.protecther;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class RiskZoneManager {
    private static final String PREF_NAME = "ProtectHer_RiskZones";
    private static final String KEY_ZONES = "cached_zones";
    private List<RiskZone> riskZones;
    private SharedPreferences prefs;
    private final Gson gson = new Gson();

    public RiskZoneManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadZones();
        if (riskZones.isEmpty()) {
            seedInitialData(); // Mock data for demo/testing
        }
    }

    private void loadZones() {
        String json = prefs.getString(KEY_ZONES, "");
        if (json.isEmpty()) {
            riskZones = new ArrayList<>();
        } else {
            riskZones = gson.fromJson(json, new TypeToken<List<RiskZone>>(){}.getType());
        }
    }

    public void saveZones(List<RiskZone> zones) {
        this.riskZones = zones;
        prefs.edit().putString(KEY_ZONES, gson.toJson(zones)).apply();
    }

    private void seedInitialData() {
        List<RiskZone> mockZones = new ArrayList<>();
        long now = System.currentTimeMillis();
        // DEMO DATA: Incident in the last 24h, high severity
        mockZones.add(new RiskZone(19.0800, 72.8800, 300, 5, now - 3600000, "Severe Theft"));
        // DEMO DATA: Incident 3 days ago, moderate severity
        mockZones.add(new RiskZone(19.0720, 72.8700, 500, 3, now - (86400000L * 3), "Moderate Harassment"));
        saveZones(mockZones);
    }

    public int calculateLocationRisk(Location currentLoc) {
        if (currentLoc == null || riskZones.isEmpty()) return 0;

        float totalWeightedRisk = 0;
        int nearbyCount = 0;

        for (RiskZone zone : riskZones) {
            float[] results = new float[1];
            Location.distanceBetween(currentLoc.getLatitude(), currentLoc.getLongitude(), 
                                   zone.latitude, zone.longitude, results);
            float distance = results[0];

            // Use radius from RiskZone. Consider incident within radius or up to 2km extension
            float detectionRadius = Math.max(zone.radius, 2000f);

            if (distance < detectionRadius) {
                nearbyCount++;
                
                // 1. Distance Factor (closer = higher). 
                // Normalized to 0-1. If inside radius, max risk from distance.
                float distanceFactor = (distance <= zone.radius) ? 1.0f : 
                                      Math.max(0, (detectionRadius - distance) / (detectionRadius - zone.radius));
                
                // 2. Severity Factor (1-5) -> 0.2 to 1.0
                float severityFactor = zone.severity / 5.0f;
                
                // 3. Recency Factor (Newer = higher, decays over 7 days)
                long ageMillis = System.currentTimeMillis() - zone.incidentDate;
                float daysOld = ageMillis / (1000f * 60 * 60 * 24);
                float recencyFactor = Math.max(0, (7 - daysOld) / 7.0f);

                // Individual zone risk
                float zoneRisk = (distanceFactor * 0.4f) + (severityFactor * 0.4f) + (recencyFactor * 0.2f);
                totalWeightedRisk += (zoneRisk * 100);
            }
        }

        if (nearbyCount > 0) {
            float averageRisk = totalWeightedRisk / nearbyCount;
            // D. Density Factor: Bonus risk for multiple incidents
            float densityBonus = Math.min(30, (nearbyCount - 1) * 10);
            return (int) Math.min(100, averageRisk + densityBonus);
        }

        return 0;
    }

    public List<RiskZone> getRiskZones() {
        return riskZones;
    }
}

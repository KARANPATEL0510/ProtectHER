package com.example.protecther;

import android.content.Context;
import android.location.Location;

import java.util.List;

/**
 * Architecture for connecting external legimate incident data.
 * Currently serves as a wrapper for local caching and demo data seeding.
 */
public class RiskZoneRepository {
    private RiskZoneManager manager;

    public RiskZoneRepository(Context context) {
        this.manager = new RiskZoneManager(context);
    }

    public List<RiskZone> getRiskZones() {
        return manager.getRiskZones();
    }

    public void refreshDataFromApi() {
        // Legitimate API architecture placeholder.
        // fetchFromApi().then(data -> manager.saveZones(data));
    }

    public int calculateLocationRisk(Location currentLoc) {
        return manager.calculateLocationRisk(currentLoc);
    }
}

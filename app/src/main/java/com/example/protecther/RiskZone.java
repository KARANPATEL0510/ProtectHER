package com.example.protecther;

public class RiskZone {
    public double latitude;
    public double longitude;
    public float radius; // in meters
    public int severity; // 1 to 5 (5 is most severe)
    public long incidentDate; // timestamp
    public String incidentType; // e.g., "Theft", "Assault", "Harassment"

    public RiskZone(double lat, double lng, float radius, int severity, long date, String type) {
        this.latitude = lat;
        this.longitude = lng;
        this.radius = radius;
        this.severity = severity;
        this.incidentDate = date;
        this.incidentType = type;
    }
}

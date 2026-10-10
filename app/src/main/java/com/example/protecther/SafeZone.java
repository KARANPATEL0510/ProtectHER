package com.example.protecther;

public class SafeZone {
    public String name;
    public double latitude;
    public double longitude;
    public float radius;

    public SafeZone(String name, double latitude, double longitude, float radius) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }
}

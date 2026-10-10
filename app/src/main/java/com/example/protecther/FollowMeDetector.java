package com.example.protecther;

import android.graphics.Rect;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FollowMeDetector {

    public static class DetectedPerson {
        public int id;
        public Rect bounds;
        public long timestamp;
        public float centerX, centerY;

        public DetectedPerson(int id, Rect bounds) {
            this.id = id;
            this.bounds = bounds;
            this.timestamp = System.currentTimeMillis();
            this.centerX = bounds.centerX();
            this.centerY = bounds.centerY();
        }
    }

    private static class PersonTrack {
        public int id;
        public List<DetectedPerson> history = new ArrayList<>();
        public long firstSeen;
        public long lastSeen;
        public float suspicionScore = 0;

        public PersonTrack(DetectedPerson person) {
            this.id = person.id;
            this.firstSeen = person.timestamp;
            update(person);
        }

        public void update(DetectedPerson person) {
            history.add(person);
            lastSeen = person.timestamp;
            if (history.size() > 20) history.remove(0);
            calculateSuspicion();
        }

        private void calculateSuspicion() {
            float score = 0;
            
            // 1. Duration factor
            long duration = lastSeen - firstSeen;
            if (duration > 60000) score += 30; // Followed for > 1 min
            else if (duration > 30000) score += 15;

            // 2. Proximity Trend (Getting closer)
            if (history.size() > 5) {
                DetectedPerson first = history.get(0);
                DetectedPerson last = history.get(history.size() - 1);
                
                // Simplified: Assuming larger bounds means closer
                float initialArea = first.bounds.width() * first.bounds.height();
                float currentArea = last.bounds.width() * last.bounds.height();
                if (currentArea > initialArea * 1.5) score += 20;
            }

            // 3. Repeated Detection / Reappearance
            // (In a real app, this would check if the person was lost and found)
            
            suspicionScore = Math.min(score, 100);
        }
    }

    private Map<Integer, PersonTrack> tracks = new HashMap<>();
    private static final long TRACK_TIMEOUT = 10000; // 10 seconds

    public float updateAndGetMaxSuspicion(List<DetectedPerson> detectedPersons) {
        long now = System.currentTimeMillis();
        
        // Update or create tracks
        for (DetectedPerson p : detectedPersons) {
            if (tracks.containsKey(p.id)) {
                tracks.get(p.id).update(p);
            } else {
                tracks.put(p.id, new PersonTrack(p));
            }
        }

        // Cleanup old tracks
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, PersonTrack> entry : tracks.entrySet()) {
            if (now - entry.getValue().lastSeen > TRACK_TIMEOUT) {
                toRemove.add(entry.getKey());
            }
        }
        for (Integer id : toRemove) tracks.remove(id);

        // Find max suspicion
        float maxScore = 0;
        for (PersonTrack track : tracks.values()) {
            if (track.suspicionScore > maxScore) maxScore = track.suspicionScore;
        }

        return maxScore;
    }
}

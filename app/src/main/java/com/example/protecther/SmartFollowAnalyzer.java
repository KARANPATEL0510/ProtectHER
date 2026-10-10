package com.example.protecther;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A conservative co-presence heuristic. Its provisional Fuzzy AHP weights are not empirically
 * validated, and radio recurrence cannot confirm that a person is following.
 */
public final class SmartFollowAnalyzer {

    public static final int POTENTIAL_THREAT_THRESHOLD = 55;
    public static final int EMERGENCY_ALERT_THRESHOLD = 85;
    private static final long MIN_REPEAT_INTERVAL_MS = 45_000L;
    private static final long MIN_PERSISTENCE_MS = 45_000L;
    private static final long MAX_TRACK_AGE_MS = 20 * 60_000L;
    private static final long MAX_WIFI_TRACK_AGE_MS = 65 * 60_000L;
    private static final double MOVEMENT_THRESHOLD_METERS = 200.0;
    private static final double MIN_MOVEMENT_SEGMENT_METERS = 20.0;
    private static final int[][] PAIRWISE_IMPORTANCE = {
            {1, 2, 1, 3},
            {0, 1, -2, 2},
            {0, 0, 1, 2},
            {0, 0, 0, 1}
    };
    private static final double[] FUZZY_AHP_WEIGHTS = calculateFuzzyAhpWeights();

    private final Map<String, Track> tracks = new HashMap<>();
    private AnalysisResult latest = new AnalysisResult(0, 0, 0, 0, 0, 0, 0, 0, 0, false);

    public synchronized AnalysisResult recordScan(
            Map<String, Integer> nearbyBleSignals,
            long scanTimeMillis,
            boolean hasLocation,
            double latitude,
            double longitude) {
        return recordScan(nearbyBleSignals, new HashMap<>(), scanTimeMillis,
                hasLocation, latitude, longitude);
    }

    public synchronized AnalysisResult recordScan(
            Map<String, Integer> nearbyBleSignals,
            Map<String, Integer> nearbyWifiSignals,
            long scanTimeMillis,
            boolean hasLocation,
            double latitude,
            double longitude) {
        Iterator<Map.Entry<String, Track>> iterator = tracks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Track> entry = iterator.next();
            long maxTrackAge = entry.getValue().wifiRadio
                    ? MAX_WIFI_TRACK_AGE_MS : MAX_TRACK_AGE_MS;
            if (scanTimeMillis - entry.getValue().lastSeenMillis > maxTrackAge) {
                iterator.remove();
            }
        }

        int nearbyCount = 0;
        int nearbyBleCount = countValidSignals(nearbyBleSignals);
        int nearbyWifiCount = countValidSignals(nearbyWifiSignals);
        int repeatedBleCount = 0;
        int repeatedWifiCount = 0;
        int movingRepeatCount = 0;
        long longestPersistenceMillis = 0;
        double greatestMovementMeters = 0;
        int strongestAverageRepeatRssi = -127;

        Map<String, Integer> observations = new HashMap<>();
        addObservations(observations, nearbyBleSignals, "ble:");
        addObservations(observations, nearbyWifiSignals, "wifi:");
        for (Map.Entry<String, Integer> signal : observations.entrySet()) {
            String id = signal.getKey();
            int rssi = signal.getValue();
            nearbyCount++;

            Track track = tracks.get(id);
            if (track == null) {
                tracks.put(id, new Track(scanTimeMillis, rssi, hasLocation, latitude, longitude,
                        id.startsWith("wifi:")));
                continue;
            }

            if (scanTimeMillis <= track.lastSeenMillis) continue;
            long gapMillis = scanTimeMillis - track.lastSeenMillis;
            if (gapMillis >= MIN_REPEAT_INTERVAL_MS) {
                track.sightings++;
                track.rssiTotal += rssi;
                track.rssiSamples++;
                if (hasLocation && track.hasLocation) {
                    double segmentMeters = distanceMeters(track.lastLatitude, track.lastLongitude,
                            latitude, longitude);
                    if (segmentMeters >= MIN_MOVEMENT_SEGMENT_METERS && segmentMeters <= 6_000.0) {
                        track.routeDistanceMeters += segmentMeters;
                    }
                }
                longestPersistenceMillis = Math.max(longestPersistenceMillis,
                        scanTimeMillis - track.firstSeenMillis);
                if (track.sightings >= 2
                        && scanTimeMillis - track.firstSeenMillis >= MIN_PERSISTENCE_MS) {
                    if (id.startsWith("wifi:")) repeatedWifiCount++;
                    else repeatedBleCount++;
                    int averageRssi = (int) (track.rssiTotal / track.rssiSamples);
                    strongestAverageRepeatRssi = Math.max(strongestAverageRepeatRssi, averageRssi);
                    greatestMovementMeters = Math.max(greatestMovementMeters,
                            track.routeDistanceMeters);
                    if (track.routeDistanceMeters >= MOVEMENT_THRESHOLD_METERS) {
                        movingRepeatCount++;
                    }
                }
            }

            track.lastSeenMillis = scanTimeMillis;
            if (hasLocation) {
                track.hasLocation = true;
                track.lastLatitude = latitude;
                track.lastLongitude = longitude;
            }
        }

        int repeatedCount = repeatedBleCount + repeatedWifiCount;
        int score = calculateScore(nearbyCount, repeatedCount, movingRepeatCount,
                longestPersistenceMillis, greatestMovementMeters, strongestAverageRepeatRssi);
        latest = new AnalysisResult(score, nearbyCount, nearbyBleCount, nearbyWifiCount,
                repeatedBleCount, repeatedWifiCount,
                movingRepeatCount, longestPersistenceMillis, greatestMovementMeters,
                repeatedCount > 0 && movingRepeatCount > 0
                        && score >= POTENTIAL_THREAT_THRESHOLD);
        return latest;
    }

    public synchronized AnalysisResult recordScan(
            Set<String> bleIds, Set<String> wifiIds, long scanTimeMillis,
            boolean hasLocation, double latitude, double longitude) {
        Map<String, Integer> bleSignals = new HashMap<>();
        for (String id : bleIds) {
            if (id != null) bleSignals.put(id, -65);
        }
        Map<String, Integer> wifiSignals = new HashMap<>();
        for (String id : wifiIds) {
            if (id != null) wifiSignals.put(id, -65);
        }
        return recordScan(bleSignals, wifiSignals, scanTimeMillis, hasLocation, latitude, longitude);
    }

    private static void addObservations(Map<String, Integer> target,
                                        Map<String, Integer> source, String prefix) {
        for (Map.Entry<String, Integer> signal : source.entrySet()) {
            String id = signal.getKey();
            Integer rssi = signal.getValue();
            if (id != null && !id.isEmpty() && rssi != null) {
                target.put(prefix + id, rssi);
            }
        }
    }

    private static int countValidSignals(Map<String, Integer> signals) {
        int count = 0;
        for (Map.Entry<String, Integer> signal : signals.entrySet()) {
            if (signal.getKey() != null && !signal.getKey().isEmpty()
                    && signal.getValue() != null) count++;
        }
        return count;
    }

    private static int calculateScore(int nearbyCount, int repeatedCount, int movingRepeatCount,
                                      long persistenceMillis, double movementMeters, int rssi) {
        if (repeatedCount == 0) return 0;

        double recurrence = Math.min(1.0, repeatedCount / 2.0);
        double persistence = Math.min(1.0, persistenceMillis / (10.0 * 60_000.0));
        double movement = Math.min(1.0, movementMeters / 500.0);
        double proximity = Math.max(0.0, Math.min(1.0, (rssi + 90.0) / 40.0));
        double weightedEvidence = recurrence * FUZZY_AHP_WEIGHTS[0]
                + persistence * FUZZY_AHP_WEIGHTS[1]
                + movement * FUZZY_AHP_WEIGHTS[2]
                + proximity * FUZZY_AHP_WEIGHTS[3];
        int score = (int) Math.round(weightedEvidence * 100.0);
        score -= Math.min(20, Math.max(0, nearbyCount - 8) * 2);
        return Math.max(0, Math.min(100, score));
    }

    static double[] fuzzyAhpWeights() {
        return FUZZY_AHP_WEIGHTS.clone();
    }

    private static double[] calculateFuzzyAhpWeights() {
        int criterionCount = PAIRWISE_IMPORTANCE.length;
        double[] rowCentroids = new double[criterionCount];
        double centroidTotal = 0.0;

        for (int row = 0; row < criterionCount; row++) {
            double[] products = {1.0, 1.0, 1.0};
            for (int column = 0; column < criterionCount; column++) {
                double[] comparison = fuzzyComparison(row, column);
                for (int component = 0; component < products.length; component++) {
                    products[component] *= comparison[component];
                }
            }

            double root = 1.0 / criterionCount;
            double low = Math.pow(products[0], root);
            double middle = Math.pow(products[1], root);
            double high = Math.pow(products[2], root);
            rowCentroids[row] = (low + middle + high) / 3.0;
            centroidTotal += rowCentroids[row];
        }

        for (int index = 0; index < rowCentroids.length; index++) {
            rowCentroids[index] /= centroidTotal;
        }
        return rowCentroids;
    }

    private static double[] fuzzyComparison(int row, int column) {
        if (row == column) return new double[]{1.0, 1.0, 1.0};
        int rating = row < column
                ? PAIRWISE_IMPORTANCE[row][column]
                : PAIRWISE_IMPORTANCE[column][row];
        boolean reciprocalRequired = row < column ? rating < 0 : rating > 0;
        double[] comparison = fuzzyScale(Math.abs(rating));
        if (!reciprocalRequired) return comparison;
        return new double[]{
                1.0 / comparison[2],
                1.0 / comparison[1],
                1.0 / comparison[0]
        };
    }

    private static double[] fuzzyScale(int importance) {
        switch (importance) {
            case 1:
                return new double[]{1.0, 1.0, 1.0};
            case 2:
                return new double[]{1.0, 2.0, 3.0};
            case 3:
                return new double[]{2.0, 3.0, 4.0};
            default:
                throw new IllegalStateException("Unsupported fuzzy pairwise importance: " + importance);
        }
    }

    private static double distanceMeters(double latitude1, double longitude1,
                                         double latitude2, double longitude2) {
        double earthRadiusMeters = 6_371_000.0;
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public synchronized AnalysisResult getLatest() {
        return latest;
    }

    public static boolean shouldTriggerEmergencyAlert(AnalysisResult result) {
        return result != null
                && result.suspicionScore >= EMERGENCY_ALERT_THRESHOLD
                && result.repeatedBleCount > 0
                && result.movingRepeatCount > 0
                && result.greatestMovementMeters >= 500.0;
    }

    public synchronized void reset() {
        tracks.clear();
        latest = new AnalysisResult(0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    private static final class Track {
        private final long firstSeenMillis;
        private final boolean wifiRadio;
        private long lastSeenMillis;
        private int sightings = 1;
        private long rssiTotal;
        private int rssiSamples = 1;
        private boolean hasLocation;
        private double lastLatitude;
        private double lastLongitude;
        private double routeDistanceMeters;

        Track(long seenAtMillis, int rssi, boolean hasLocation, double latitude, double longitude,
              boolean wifiRadio) {
            firstSeenMillis = seenAtMillis;
            this.wifiRadio = wifiRadio;
            lastSeenMillis = seenAtMillis;
            rssiTotal = rssi;
            this.hasLocation = hasLocation;
            lastLatitude = latitude;
            lastLongitude = longitude;
        }
    }

    public static final class AnalysisResult {
        public final int suspicionScore;
        public final int currentNearbyCount;
        public final int currentNearbyBleCount;
        public final int currentNearbyWifiCount;
        public final int repeatedBleCount;
        public final int repeatedWifiCount;
        public final int movingRepeatCount;
        public final long repeatDurationMillis;
        public final double greatestMovementMeters;
        public final boolean hasRepeatEvidence;

        AnalysisResult(int suspicionScore, int currentNearbyCount, int currentNearbyBleCount,
                       int currentNearbyWifiCount, int repeatedBleCount, int repeatedWifiCount,
                       int movingRepeatCount, long repeatDurationMillis,
                       double greatestMovementMeters, boolean hasRepeatEvidence) {
            this.suspicionScore = suspicionScore;
            this.currentNearbyCount = currentNearbyCount;
            this.currentNearbyBleCount = currentNearbyBleCount;
            this.currentNearbyWifiCount = currentNearbyWifiCount;
            this.repeatedBleCount = repeatedBleCount;
            this.repeatedWifiCount = repeatedWifiCount;
            this.movingRepeatCount = movingRepeatCount;
            this.repeatDurationMillis = repeatDurationMillis;
            this.greatestMovementMeters = greatestMovementMeters;
            this.hasRepeatEvidence = hasRepeatEvidence;
        }
    }
}

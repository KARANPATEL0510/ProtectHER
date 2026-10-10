package com.example.protecther;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartFollowAnalyzerTest {

    @Test
    public void firstObservationDoesNotCreateThreatEvidence() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                signals("ble-a", -65), 1_000L, true, 0.0, 0.0);

        assertEquals(0, result.suspicionScore);
        assertEquals(0, result.repeatedBleCount);
        assertFalse(result.hasRepeatEvidence);
    }

    @Test
    public void persistentSignalWithoutMovementDoesNotRaisePotentialThreat() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        analyzer.recordScan(signals("ble-a", -65), 1_000L, true, 0.0, 0.0);
        analyzer.recordScan(signals("ble-a", -65), 151_000L, true, 0.0, 0.0);

        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                signals("ble-a", -65), 301_000L, true, 0.0, 0.0);

        assertEquals(1, result.repeatedBleCount);
        assertEquals(0, result.movingRepeatCount);
        assertFalse(result.hasRepeatEvidence);
        assertFalse(SmartFollowAnalyzer.shouldTriggerEmergencyAlert(result));
    }

    @Test
    public void repeatedSignalProducesDetectionScoreWithinOneMinute() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        Map<String, Integer> signal = signals("ble-a", -65);
        analyzer.recordScan(signal, 1_000L, true, 0.0, 0.0);

        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                signal, 61_000L, true, 0.0, 0.00045);

        assertEquals(1, result.repeatedBleCount);
        assertTrue(result.suspicionScore > 0);
    }

    @Test
    public void repeatedSignalsCanBeAnalyzedInUnderTwoMinutes() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        Map<String, Integer> signals = new HashMap<>();
        signals.put("ble-a", -65);
        signals.put("ble-b", -67);
        analyzer.recordScan(signals, 1_000L, true, 0.0, 0.0);
        analyzer.recordScan(signals, 61_000L, true, 0.0, 0.003);

        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                signals, 121_000L, true, 0.0, 0.006);

        assertEquals(2, result.repeatedBleCount);
        assertEquals(2, result.movingRepeatCount);
        assertTrue(result.hasRepeatEvidence);
        assertTrue(result.suspicionScore >= SmartFollowAnalyzer.POTENTIAL_THREAT_THRESHOLD);
    }

    @Test
    public void walkingSizedGpsSegmentsAccumulateMovementWithinFiveMinutes() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        Map<String, Integer> signals = signals("ble-a", -65);
        SmartFollowAnalyzer.AnalysisResult result = null;

        for (int scan = 0; scan <= 5; scan++) {
            result = analyzer.recordScan(signals, 1_000L + scan * 60_000L,
                    true, 0.0, scan * 0.00045);
        }

        assertTrue(result.greatestMovementMeters >= 200.0);
        assertEquals(1, result.movingRepeatCount);
        assertEquals(1, result.repeatedBleCount);
    }

    @Test
    public void repeatedSignalAcrossMovementIsOnlyPotentialNotConfirmed() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        analyzer.recordScan(signals("ble-a", -65), 1_000L, true, 0.0, 0.0);
        analyzer.recordScan(signals("ble-a", -65), 151_000L, true, 0.0, 0.003);

        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                signals("ble-a", -65), 301_000L, true, 0.0, 0.006);

        assertEquals(1, result.repeatedBleCount);
        assertEquals(1, result.movingRepeatCount);
        assertTrue(result.greatestMovementMeters > 500.0);
        assertTrue(result.hasRepeatEvidence);
        assertFalse(SmartFollowAnalyzer.shouldTriggerEmergencyAlert(result));
    }

    @Test
    public void distinctRadiosAreRequiredForAutomaticSosThreshold() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        for (int scan = 0; scan < 7; scan++) {
            Map<String, Integer> signals = new HashMap<>();
            signals.put("ble-a", -65);
            signals.put("ble-b", -67);
            double longitude = scan * 0.003;
            SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                    signals, 1_000L + scan * 150_000L, true, 0.0, longitude);
            if (scan == 6) {
                assertTrue(result.suspicionScore >= SmartFollowAnalyzer.EMERGENCY_ALERT_THRESHOLD);
                assertTrue(SmartFollowAnalyzer.shouldTriggerEmergencyAlert(result));
            }
        }
    }

    @Test
    public void weakSignalsAreScoredLowerThanCloseSignals() {
        SmartFollowAnalyzer closeAnalyzer = new SmartFollowAnalyzer();
        SmartFollowAnalyzer weakAnalyzer = new SmartFollowAnalyzer();
        long[] times = {1_000L, 151_000L, 301_000L};
        double[] longitudes = {0.0, 0.003, 0.006};
        SmartFollowAnalyzer.AnalysisResult close = null;
        SmartFollowAnalyzer.AnalysisResult weak = null;
        for (int index = 0; index < times.length; index++) {
            close = closeAnalyzer.recordScan(signals("ble-a", -65), times[index],
                    true, 0.0, longitudes[index]);
            weak = weakAnalyzer.recordScan(signals("ble-a", -80), times[index],
                    true, 0.0, longitudes[index]);
        }

        assertTrue(close.suspicionScore > weak.suspicionScore);
    }

    @Test
    public void staleRadioObservationsAreDiscarded() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        analyzer.recordScan(signals("ble-a", -65), 1_000L, true, 0.0, 0.0);

        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                Collections.emptyMap(), 25 * 60_000L, true, 0.0, 0.0);

        assertEquals(0, result.suspicionScore);
        assertEquals(0, result.repeatedBleCount);
    }

    @Test
    public void fuzzyAhpWeightsAreNormalizedAndPrioritizeRepeatedEvidence() {
        double[] weights = SmartFollowAnalyzer.fuzzyAhpWeights();
        double total = 0.0;
        for (double weight : weights) {
            assertTrue(weight > 0.0);
            total += weight;
        }

        assertEquals(1.0, total, 0.000001);
        assertTrue(weights[0] > weights[3]);
        assertTrue(weights[2] > weights[1]);
    }

    @Test
    public void fuzzyWeightedScoreStaysBoundedAndRequiresRepeatEvidence() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        analyzer.recordScan(signals("ble-a", -60), 1_000L, true, 0.0, 0.0);
        analyzer.recordScan(signals("ble-a", -60), 151_000L, true, 0.0, 0.003);
        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                signals("ble-a", -60), 301_000L, true, 0.0, 0.006);

        assertTrue(result.suspicionScore > 0);
        assertTrue(result.suspicionScore <= 100);
        assertTrue(result.hasRepeatEvidence);
    }

    @Test
    public void repeatedWifiAccessPointIsIncludedInAnalysis() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        Map<String, Integer> wifiSignals = signals("ap-a", -65);
        analyzer.recordScan(Collections.emptyMap(), wifiSignals, 1_000L,
                true, 0.0, 0.0);
        analyzer.recordScan(Collections.emptyMap(), wifiSignals, 151_000L,
                true, 0.0, 0.003);

        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                Collections.emptyMap(), wifiSignals, 301_000L,
                true, 0.0, 0.006);

        assertEquals(0, result.repeatedBleCount);
        assertEquals(1, result.repeatedWifiCount);
        assertEquals(0, result.currentNearbyBleCount);
        assertEquals(1, result.currentNearbyWifiCount);
        assertTrue(result.hasRepeatEvidence);
    }

    @Test
    public void bluetoothAndWifiIdsAreTrackedSeparately() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        Map<String, Integer> sameId = signals("radio-a", -65);
        analyzer.recordScan(sameId, sameId, 1_000L, true, 0.0, 0.0);
        analyzer.recordScan(sameId, sameId, 151_000L, true, 0.0, 0.003);

        SmartFollowAnalyzer.AnalysisResult result = analyzer.recordScan(
                sameId, sameId, 301_000L, true, 0.0, 0.006);

        assertEquals(1, result.repeatedBleCount);
        assertEquals(1, result.repeatedWifiCount);
        assertEquals(1, result.currentNearbyBleCount);
        assertEquals(1, result.currentNearbyWifiCount);
    }

    @Test
    public void wifiOnlyEvidenceCannotTriggerAutomaticEmergencySms() {
        SmartFollowAnalyzer analyzer = new SmartFollowAnalyzer();
        Map<String, Integer> wifiSignals = new HashMap<>();
        wifiSignals.put("ap-a", -60);
        wifiSignals.put("ap-b", -61);
        wifiSignals.put("ap-c", -62);
        wifiSignals.put("ap-d", -63);
        SmartFollowAnalyzer.AnalysisResult result = null;
        for (int scan = 0; scan < 7; scan++) {
            double longitude = scan * 0.003;
            result = analyzer.recordScan(Collections.emptyMap(), wifiSignals,
                    1_000L + scan * 150_000L, true, 0.0, longitude);
        }

        assertTrue(result.suspicionScore >= SmartFollowAnalyzer.EMERGENCY_ALERT_THRESHOLD);
        assertEquals(0, result.repeatedBleCount);
        assertTrue(result.repeatedWifiCount > 0);
        assertFalse(SmartFollowAnalyzer.shouldTriggerEmergencyAlert(result));
    }

    private static Map<String, Integer> signals(String id, int rssi) {
        Map<String, Integer> signals = new HashMap<>();
        signals.put(id, rssi);
        return signals;
    }
}

package com.pitwall.app.fusion;

/**
 * 1-D Kalman filter for fusing Racelogic GPS speed and OBDLink CAN speed.
 * Weights: Racelogic 0.7, OBDLink 0.3 (GPS is the primary reference).
 *
 * State: [speed]
 * Measurement: [speed from sensor]
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0007\n\u0002\b\b\n\u0002\u0010\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u001b\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0005\u0010\u0006J\u000e\u0010\t\u001a\u00020\u00032\u0006\u0010\n\u001a\u00020\u0003J\u0010\u0010\u000b\u001a\u00020\f2\b\b\u0002\u0010\r\u001a\u00020\u0003R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0003X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0003X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0003X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/pitwall/app/fusion/KalmanFilter;", "", "processNoise", "", "measurementNoise", "<init>", "(FF)V", "x", "p", "update", "measurement", "reset", "", "initial", "app_debug"})
public final class KalmanFilter {
    private float processNoise;
    private float measurementNoise;
    private float x = 0.0F;
    private float p = 1.0F;
    
    public KalmanFilter(float processNoise, float measurementNoise) {
        super();
    }
    
    public final float update(float measurement) {
        return 0.0F;
    }
    
    public final void reset(float initial) {
    }
    
    public KalmanFilter() {
        super();
    }
}
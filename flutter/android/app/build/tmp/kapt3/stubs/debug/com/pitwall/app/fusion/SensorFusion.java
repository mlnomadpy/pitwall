package com.pitwall.app.fusion;

/**
 * Sensor Fusion Engine — merges RawFrames from Racelogic + OBDLink into
 * confidence-annotated TelemetryFrames at 10Hz.
 *
 * Pipeline per frame:
 *  1. Receive RawFrame from either sensor
 *  2. Run Kalman (speed), Butterworth (G-forces), Complementary (position)
 *  3. Annotate each signal with confidence
 *  4. Emit TelemetryFrame to downstream consumers (hot path + Antigravity)
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000d\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\r\u0018\u00002\u00020\u0001B\u000f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0004\u0010\u0005J\u0016\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010$J\u0016\u0010%\u001a\u00020\"2\u0006\u0010#\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010$J\u000e\u0010&\u001a\u00020\"H\u0082@\u00a2\u0006\u0002\u0010\'J\u0010\u0010(\u001a\u00020\u00182\u0006\u0010)\u001a\u00020\u0011H\u0002J(\u0010*\u001a\u00020\u00182\u0006\u0010+\u001a\u00020\u00132\u0006\u0010,\u001a\u00020\u00132\u0006\u0010-\u001a\u00020\u00132\u0006\u0010.\u001a\u00020\u0013H\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001c0\u001bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u001c0\u001e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010 \u00a8\u0006/"}, d2 = {"Lcom/pitwall/app/fusion/SensorFusion;", "", "track", "Lcom/pitwall/app/data/TrackMap;", "<init>", "(Lcom/pitwall/app/data/TrackMap;)V", "speedFusion", "Lcom/pitwall/app/fusion/WeightedSpeedFusion;", "gLatFilter", "Lcom/pitwall/app/fusion/ButterworthFilter;", "gLongFilter", "positionFilter", "Lcom/pitwall/app/fusion/ComplementaryFilter;", "latestRacelogic", "Lcom/pitwall/app/data/RawFrame;", "latestObd", "lap", "", "lapStartTime", "", "wasNearSF", "", "sfCooldown", "cumulativeDistance", "", "lastTimestamp", "_frames", "Lkotlinx/coroutines/flow/MutableSharedFlow;", "Lcom/pitwall/app/data/TelemetryFrame;", "frames", "Lkotlinx/coroutines/flow/SharedFlow;", "getFrames", "()Lkotlinx/coroutines/flow/SharedFlow;", "onRacelogicFrame", "", "raw", "(Lcom/pitwall/app/data/RawFrame;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "onObdFrame", "emitFusedFrame", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "gpsConfidence", "satellites", "haversineM", "lat1", "lon1", "lat2", "lon2", "app_debug"})
public final class SensorFusion {
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.data.TrackMap track = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.fusion.WeightedSpeedFusion speedFusion = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.fusion.ButterworthFilter gLatFilter = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.fusion.ButterworthFilter gLongFilter = null;
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.fusion.ComplementaryFilter positionFilter = null;
    @org.jetbrains.annotations.Nullable()
    private com.pitwall.app.data.RawFrame latestRacelogic;
    @org.jetbrains.annotations.Nullable()
    private com.pitwall.app.data.RawFrame latestObd;
    private int lap = 0;
    private double lapStartTime = 0.0;
    private boolean wasNearSF = false;
    private int sfCooldown = 0;
    private float cumulativeDistance = 0.0F;
    private double lastTimestamp = 0.0;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableSharedFlow<com.pitwall.app.data.TelemetryFrame> _frames = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.SharedFlow<com.pitwall.app.data.TelemetryFrame> frames = null;
    
    public SensorFusion(@org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.TrackMap track) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.SharedFlow<com.pitwall.app.data.TelemetryFrame> getFrames() {
        return null;
    }
    
    /**
     * Called from Racelogic Bluetooth service on each incoming VBO line.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object onRacelogicFrame(@org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.RawFrame raw, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Called from OBDLink Bluetooth service on each incoming CAN frame.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object onObdFrame(@org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.RawFrame raw, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object emitFusedFrame(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * GPS confidence from satellite quality flag (not literal count — VBO encodes quality).
     */
    private final float gpsConfidence(int satellites) {
        return 0.0F;
    }
    
    private final float haversineM(double lat1, double lon1, double lat2, double lon2) {
        return 0.0F;
    }
}
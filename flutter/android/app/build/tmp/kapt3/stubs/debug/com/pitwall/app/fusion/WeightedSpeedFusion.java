package com.pitwall.app.fusion;

/**
 * Fuse two speed measurements with fixed weights.
 * Primary: Racelogic GPS (weight 0.7), Secondary: OBDLink CAN (weight 0.3).
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\u001b\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0005\u0010\u0006J\u001f\u0010\t\u001a\u00020\u00032\b\u0010\n\u001a\u0004\u0018\u00010\u00032\b\u0010\u000b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\fR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/pitwall/app/fusion/WeightedSpeedFusion;", "", "primaryWeight", "", "secondaryWeight", "<init>", "(FF)V", "kalman", "Lcom/pitwall/app/fusion/KalmanFilter;", "update", "primary", "secondary", "(Ljava/lang/Float;Ljava/lang/Float;)F", "app_debug"})
public final class WeightedSpeedFusion {
    private final float primaryWeight = 0.0F;
    private final float secondaryWeight = 0.0F;
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.fusion.KalmanFilter kalman = null;
    
    public WeightedSpeedFusion(float primaryWeight, float secondaryWeight) {
        super();
    }
    
    public final float update(@org.jetbrains.annotations.Nullable()
    java.lang.Float primary, @org.jetbrains.annotations.Nullable()
    java.lang.Float secondary) {
        return 0.0F;
    }
    
    public WeightedSpeedFusion() {
        super();
    }
}
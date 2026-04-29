package com.pitwall.app.data;

/**
 * A signal value with confidence metadata.
 * Every signal from Racelogic and OBDLink carries this wrapper (ADR-001).
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0013\n\u0002\u0010\b\n\u0002\b\u0003\b\u0086\b\u0018\u0000 \u001f2\u00020\u0001:\u0001\u001fB1\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\u0003\u0012\b\b\u0002\u0010\b\u001a\u00020\t\u00a2\u0006\u0004\b\n\u0010\u000bJ\t\u0010\u0014\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0015\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0016\u001a\u00020\u0006H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\tH\u00c6\u0003J;\u0010\u0019\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\u00032\b\b\u0002\u0010\b\u001a\u00020\tH\u00c6\u0001J\u0013\u0010\u001a\u001a\u00020\t2\b\u0010\u001b\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001c\u001a\u00020\u001dH\u00d6\u0001J\t\u0010\u001e\u001a\u00020\u0006H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\rR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\rR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013\u00a8\u0006 "}, d2 = {"Lcom/pitwall/app/data/SignalValue;", "", "value", "", "confidence", "source", "", "hz", "stale", "", "<init>", "(FFLjava/lang/String;FZ)V", "getValue", "()F", "getConfidence", "getSource", "()Ljava/lang/String;", "getHz", "getStale", "()Z", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "other", "hashCode", "", "toString", "Companion", "app_debug"})
public final class SignalValue {
    private final float value = 0.0F;
    private final float confidence = 0.0F;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String source = null;
    private final float hz = 0.0F;
    private final boolean stale = false;
    @org.jetbrains.annotations.NotNull()
    private static final com.pitwall.app.data.SignalValue UNKNOWN = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pitwall.app.data.SignalValue.Companion Companion = null;
    
    public SignalValue(float value, float confidence, @org.jetbrains.annotations.NotNull()
    java.lang.String source, float hz, boolean stale) {
        super();
    }
    
    public final float getValue() {
        return 0.0F;
    }
    
    public final float getConfidence() {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSource() {
        return null;
    }
    
    public final float getHz() {
        return 0.0F;
    }
    
    public final boolean getStale() {
        return false;
    }
    
    public final float component1() {
        return 0.0F;
    }
    
    public final float component2() {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component3() {
        return null;
    }
    
    public final float component4() {
        return 0.0F;
    }
    
    public final boolean component5() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pitwall.app.data.SignalValue copy(float value, float confidence, @org.jetbrains.annotations.NotNull()
    java.lang.String source, float hz, boolean stale) {
        return null;
    }
    
    @java.lang.Override()
    public boolean equals(@org.jetbrains.annotations.Nullable()
    java.lang.Object other) {
        return false;
    }
    
    @java.lang.Override()
    public int hashCode() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String toString() {
        return null;
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0018\u0010\b\u001a\u00020\u00052\u0006\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\nJ\u0018\u0010\f\u001a\u00020\u00052\u0006\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\nJ\u0018\u0010\r\u001a\u00020\u00052\u0006\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\nJ\"\u0010\u000e\u001a\u00020\u00052\u0006\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000f\u001a\u00020\u00102\b\b\u0002\u0010\u000b\u001a\u00020\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u0011"}, d2 = {"Lcom/pitwall/app/data/SignalValue$Companion;", "", "<init>", "()V", "UNKNOWN", "Lcom/pitwall/app/data/SignalValue;", "getUNKNOWN", "()Lcom/pitwall/app/data/SignalValue;", "racelogicGps", "value", "", "confidence", "racelogicImu", "obdlinkCan", "fused", "source", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pitwall.app.data.SignalValue getUNKNOWN() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pitwall.app.data.SignalValue racelogicGps(float value, float confidence) {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pitwall.app.data.SignalValue racelogicImu(float value, float confidence) {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pitwall.app.data.SignalValue obdlinkCan(float value, float confidence) {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pitwall.app.data.SignalValue fused(float value, @org.jetbrains.annotations.NotNull()
        java.lang.String source, float confidence) {
            return null;
        }
    }
}
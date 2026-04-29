package com.pitwall.app.data;

@kotlinx.serialization.Serializable()
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0012\n\u0002\u0010\u000b\n\u0002\b\u000b\n\u0002\u0010\b\n\u0002\b\u0004\b\u0087\b\u0018\u0000 /2\u00020\u0001:\u0002./BC\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n\u0012\f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\n\u00a2\u0006\u0004\b\u000e\u0010\u000fJ\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u001b\u001a\u00020\u0005J\u0010\u0010\u001c\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u001b\u001a\u00020\u0005J\u0016\u0010\u001d\u001a\u00020\u00052\u0006\u0010\u001b\u001a\u00020\u00052\u0006\u0010\u001e\u001a\u00020\u000bJ\u0016\u0010\u001f\u001a\u00020 2\u0006\u0010\u001b\u001a\u00020\u00052\u0006\u0010\u001e\u001a\u00020\u000bJ\u0010\u0010!\u001a\u0004\u0018\u00010\r2\u0006\u0010\u001b\u001a\u00020\u0005J\t\u0010\"\u001a\u00020\u0003H\u00c6\u0003J\t\u0010#\u001a\u00020\u0005H\u00c6\u0003J\t\u0010$\u001a\u00020\u0007H\u00c6\u0003J\t\u0010%\u001a\u00020\u0007H\u00c6\u0003J\u000f\u0010&\u001a\b\u0012\u0004\u0012\u00020\u000b0\nH\u00c6\u0003J\u000f\u0010\'\u001a\b\u0012\u0004\u0012\u00020\r0\nH\u00c6\u0003JQ\u0010(\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00072\u000e\b\u0002\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n2\u000e\b\u0002\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\nH\u00c6\u0001J\u0013\u0010)\u001a\u00020 2\b\u0010*\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010+\u001a\u00020,H\u00d6\u0001J\t\u0010-\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0015R\u0017\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0017\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u0018\u00a8\u00060"}, d2 = {"Lcom/pitwall/app/data/TrackMap;", "", "name", "", "trackLength", "", "sfLat", "", "sfLon", "corners", "", "Lcom/pitwall/app/data/TrackCorner;", "sectors", "Lcom/pitwall/app/data/TrackSector;", "<init>", "(Ljava/lang/String;FDDLjava/util/List;Ljava/util/List;)V", "getName", "()Ljava/lang/String;", "getTrackLength", "()F", "getSfLat", "()D", "getSfLon", "getCorners", "()Ljava/util/List;", "getSectors", "cornerAt", "distance", "nearestCorner", "distanceToCorner", "corner", "isPastApex", "", "sectorAt", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "other", "hashCode", "", "toString", "$serializer", "Companion", "app_debug"})
public final class TrackMap {
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String name = null;
    private final float trackLength = 0.0F;
    private final double sfLat = 0.0;
    private final double sfLon = 0.0;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.pitwall.app.data.TrackCorner> corners = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.pitwall.app.data.TrackSector> sectors = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.pitwall.app.data.TrackMap.Companion Companion = null;
    
    public TrackMap(@org.jetbrains.annotations.NotNull()
    java.lang.String name, float trackLength, double sfLat, double sfLon, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pitwall.app.data.TrackCorner> corners, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pitwall.app.data.TrackSector> sectors) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getName() {
        return null;
    }
    
    public final float getTrackLength() {
        return 0.0F;
    }
    
    public final double getSfLat() {
        return 0.0;
    }
    
    public final double getSfLon() {
        return 0.0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pitwall.app.data.TrackCorner> getCorners() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pitwall.app.data.TrackSector> getSectors() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pitwall.app.data.TrackCorner cornerAt(float distance) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pitwall.app.data.TrackCorner nearestCorner(float distance) {
        return null;
    }
    
    public final float distanceToCorner(float distance, @org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.TrackCorner corner) {
        return 0.0F;
    }
    
    public final boolean isPastApex(float distance, @org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.TrackCorner corner) {
        return false;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.pitwall.app.data.TrackSector sectorAt(float distance) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component1() {
        return null;
    }
    
    public final float component2() {
        return 0.0F;
    }
    
    public final double component3() {
        return 0.0;
    }
    
    public final double component4() {
        return 0.0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pitwall.app.data.TrackCorner> component5() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.pitwall.app.data.TrackSector> component6() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pitwall.app.data.TrackMap copy(@org.jetbrains.annotations.NotNull()
    java.lang.String name, float trackLength, double sfLat, double sfLon, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pitwall.app.data.TrackCorner> corners, @org.jetbrains.annotations.NotNull()
    java.util.List<com.pitwall.app.data.TrackSector> sectors) {
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
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0011\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0003\u0010\u0004J\u0015\u0010\u0005\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u00070\u0006\u00a2\u0006\u0002\u0010\bJ\u000e\u0010\t\u001a\u00020\u00022\u0006\u0010\n\u001a\u00020\u000bJ\u0016\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0002R\u0011\u0010\u0011\u001a\u00020\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014\u00a8\u0006\u0015"}, d2 = {"com/pitwall/app/data/TrackMap.$serializer", "Lkotlinx/serialization/internal/GeneratedSerializer;", "Lcom/pitwall/app/data/TrackMap;", "<init>", "()V", "childSerializers", "", "Lkotlinx/serialization/KSerializer;", "()[Lkotlinx/serialization/KSerializer;", "deserialize", "decoder", "Lkotlinx/serialization/encoding/Decoder;", "serialize", "", "encoder", "Lkotlinx/serialization/encoding/Encoder;", "value", "descriptor", "Lkotlinx/serialization/descriptors/SerialDescriptor;", "getDescriptor", "()Lkotlinx/serialization/descriptors/SerialDescriptor;", "app_debug"})
    @java.lang.Deprecated()
    public static final class $serializer implements kotlinx.serialization.internal.GeneratedSerializer<com.pitwall.app.data.TrackMap> {
        @org.jetbrains.annotations.NotNull()
        public static final com.pitwall.app.data.TrackMap.$serializer INSTANCE = null;
        @org.jetbrains.annotations.NotNull()
        private static final kotlinx.serialization.descriptors.SerialDescriptor descriptor = null;
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public final kotlinx.serialization.KSerializer<?>[] childSerializers() {
            return null;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public final com.pitwall.app.data.TrackMap deserialize(@org.jetbrains.annotations.NotNull()
        kotlinx.serialization.encoding.Decoder decoder) {
            return null;
        }
        
        @java.lang.Override()
        public final void serialize(@org.jetbrains.annotations.NotNull()
        kotlinx.serialization.encoding.Encoder encoder, @org.jetbrains.annotations.NotNull()
        com.pitwall.app.data.TrackMap value) {
        }
        
        private $serializer() {
            super();
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public final kotlinx.serialization.descriptors.SerialDescriptor getDescriptor() {
            return null;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public kotlinx.serialization.KSerializer<?>[] typeParametersSerializers() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003J\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a8\u0006\u0007"}, d2 = {"Lcom/pitwall/app/data/TrackMap$Companion;", "", "<init>", "()V", "serializer", "Lkotlinx/serialization/KSerializer;", "Lcom/pitwall/app/data/TrackMap;", "app_debug"})
    public static final class Companion {
        
        @org.jetbrains.annotations.NotNull()
        public final kotlinx.serialization.KSerializer<com.pitwall.app.data.TrackMap> serializer() {
            return null;
        }
        
        private Companion() {
            super();
        }
    }
}
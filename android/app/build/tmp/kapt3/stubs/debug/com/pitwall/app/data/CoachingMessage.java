package com.pitwall.app.data;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u000e\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\b\r\b\u0086\b\u0018\u0000 (2\u00020\u0001:\u0002\'(B3\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\u0003\u0012\b\b\u0002\u0010\t\u001a\u00020\n\u00a2\u0006\u0004\b\u000b\u0010\fJ\u0014\u0010\u001b\u001a\u0010\u0012\u0004\u0012\u00020\u0003\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u001cJ\t\u0010\u001d\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001e\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001f\u001a\u00020\u0007H\u00c6\u0003J\u000b\u0010 \u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010!\u001a\u00020\nH\u00c6\u0003J=\u0010\"\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\t\u001a\u00020\nH\u00c6\u0001J\u0013\u0010#\u001a\u00020\u00192\b\u0010$\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010%\u001a\u00020\u0005H\u00d6\u0001J\t\u0010&\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0013\u0010\b\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u000eR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\u0016\u001a\u00020\n8F\u00a2\u0006\u0006\u001a\u0004\b\u0017\u0010\u0015R\u0011\u0010\u0018\u001a\u00020\u00198F\u00a2\u0006\u0006\u001a\u0004\b\u0018\u0010\u001a\u00a8\u0006)"}, d2 = {"Lcom/pitwall/app/data/CoachingMessage;", "", "text", "", "priority", "", "source", "Lcom/pitwall/app/data/CoachingMessage$Source;", "targetCorner", "createdAt", "", "<init>", "(Ljava/lang/String;ILcom/pitwall/app/data/CoachingMessage$Source;Ljava/lang/String;J)V", "getText", "()Ljava/lang/String;", "getPriority", "()I", "getSource", "()Lcom/pitwall/app/data/CoachingMessage$Source;", "getTargetCorner", "getCreatedAt", "()J", "ageMs", "getAgeMs", "isStale", "", "()Z", "toChannelMap", "", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "other", "hashCode", "toString", "Source", "Companion", "app_debug"})
public final class CoachingMessage {
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String text = null;
    private final int priority = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.data.CoachingMessage.Source source = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String targetCorner = null;
    private final long createdAt = 0L;
    public static final long STALE_THRESHOLD_MS = 5000L;
    public static final long COOLDOWN_MS = 3000L;
    @org.jetbrains.annotations.NotNull()
    public static final com.pitwall.app.data.CoachingMessage.Companion Companion = null;
    
    public CoachingMessage(@org.jetbrains.annotations.NotNull()
    java.lang.String text, int priority, @org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.CoachingMessage.Source source, @org.jetbrains.annotations.Nullable()
    java.lang.String targetCorner, long createdAt) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getText() {
        return null;
    }
    
    public final int getPriority() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pitwall.app.data.CoachingMessage.Source getSource() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getTargetCorner() {
        return null;
    }
    
    public final long getCreatedAt() {
        return 0L;
    }
    
    public final long getAgeMs() {
        return 0L;
    }
    
    public final boolean isStale() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.lang.String, java.lang.Object> toChannelMap() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component1() {
        return null;
    }
    
    public final int component2() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pitwall.app.data.CoachingMessage.Source component3() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component4() {
        return null;
    }
    
    public final long component5() {
        return 0L;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.pitwall.app.data.CoachingMessage copy(@org.jetbrains.annotations.NotNull()
    java.lang.String text, int priority, @org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.CoachingMessage.Source source, @org.jetbrains.annotations.Nullable()
    java.lang.String targetCorner, long createdAt) {
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
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0007"}, d2 = {"Lcom/pitwall/app/data/CoachingMessage$Companion;", "", "<init>", "()V", "STALE_THRESHOLD_MS", "", "COOLDOWN_MS", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0005\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003j\u0002\b\u0004j\u0002\b\u0005\u00a8\u0006\u0006"}, d2 = {"Lcom/pitwall/app/data/CoachingMessage$Source;", "", "<init>", "(Ljava/lang/String;I)V", "HOT_PATH", "WARM_PATH", "app_debug"})
    public static enum Source {
        /*public static final*/ HOT_PATH /* = new HOT_PATH() */,
        /*public static final*/ WARM_PATH /* = new WARM_PATH() */;
        
        Source() {
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.pitwall.app.data.CoachingMessage.Source> getEntries() {
            return null;
        }
    }
}
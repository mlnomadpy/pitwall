package com.pitwall.app.ui;

/**
 * Sealed event that MainActivity observes to perform Activity-context operations
 * (starting foreground service, binding, unbinding).
 * ViewModel never holds an Activity or service reference directly.
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0002\u0004\u0005B\t\b\u0004\u00a2\u0006\u0004\b\u0002\u0010\u0003\u0082\u0001\u0002\u0006\u0007\u00a8\u0006\b"}, d2 = {"Lcom/pitwall/app/ui/SessionCommand;", "", "<init>", "()V", "Start", "Stop", "Lcom/pitwall/app/ui/SessionCommand$Start;", "Lcom/pitwall/app/ui/SessionCommand$Stop;", "app_debug"})
public abstract class SessionCommand {
    
    private SessionCommand() {
        super();
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\n\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u0019\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0005\u0010\u0006J\u000b\u0010\n\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\u001f\u0010\f\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\r\u001a\u00020\u000e2\b\u0010\u000f\u001a\u0004\u0018\u00010\u0010H\u00d6\u0003J\t\u0010\u0011\u001a\u00020\u0012H\u00d6\u0001J\t\u0010\u0013\u001a\u00020\u0003H\u00d6\u0001R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\b\u00a8\u0006\u0014"}, d2 = {"Lcom/pitwall/app/ui/SessionCommand$Start;", "Lcom/pitwall/app/ui/SessionCommand;", "replayPath", "", "level", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", "getReplayPath", "()Ljava/lang/String;", "getLevel", "component1", "component2", "copy", "equals", "", "other", "", "hashCode", "", "toString", "app_debug"})
    public static final class Start extends com.pitwall.app.ui.SessionCommand {
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String replayPath = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String level = null;
        
        public Start(@org.jetbrains.annotations.Nullable()
        java.lang.String replayPath, @org.jetbrains.annotations.NotNull()
        java.lang.String level) {
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getReplayPath() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getLevel() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pitwall.app.ui.SessionCommand.Start copy(@org.jetbrains.annotations.Nullable()
        java.lang.String replayPath, @org.jetbrains.annotations.NotNull()
        java.lang.String level) {
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
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003\u00a8\u0006\u0004"}, d2 = {"Lcom/pitwall/app/ui/SessionCommand$Stop;", "Lcom/pitwall/app/ui/SessionCommand;", "<init>", "()V", "app_debug"})
    public static final class Stop extends com.pitwall.app.ui.SessionCommand {
        @org.jetbrains.annotations.NotNull()
        public static final com.pitwall.app.ui.SessionCommand.Stop INSTANCE = null;
        
        private Stop() {
        }
    }
}
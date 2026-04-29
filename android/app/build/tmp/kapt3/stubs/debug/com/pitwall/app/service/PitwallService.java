package com.pitwall.app.service;

/**
 * Foreground service — thin VBO/BT relay to the Python bridge.
 *
 * Exposes:
 *  [telemetry]  StateFlow<TelemetryFrame?> — consumed by PitwallViewModel (Compose)
 *  [coaching]   SharedFlow<CoachingMessage> — consumed by PitwallViewModel (Compose)
 *
 * No on-device ML, no audio, no EventChannel sinks — all coaching lives in pitwall_bridge.py.
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u00ac\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0010$\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000 K2\u00020\u0001:\u0002JKB\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\b\u0010+\u001a\u00020,H\u0016J\"\u0010-\u001a\u00020\"2\b\u0010.\u001a\u0004\u0018\u00010/2\u0006\u00100\u001a\u00020\"2\u0006\u00101\u001a\u00020\"H\u0016J\u0010\u00102\u001a\u0002032\u0006\u0010.\u001a\u00020/H\u0016J\u0018\u00104\u001a\u00020,2\b\u00105\u001a\u0004\u0018\u00010\u001eH\u0082@\u00a2\u0006\u0002\u00106J\u0016\u00107\u001a\u00020,2\u0006\u00108\u001a\u00020\u000eH\u0082@\u00a2\u0006\u0002\u00109J\u000e\u0010:\u001a\u00020,2\u0006\u0010;\u001a\u00020\u001eJ\u0006\u0010<\u001a\u00020,J\u0014\u0010=\u001a\u0010\u0012\u0004\u0012\u00020\u001e\u0012\u0006\u0012\u0004\u0018\u00010?0>J\u0016\u0010@\u001a\u00020\u001e2\f\u0010A\u001a\b\u0012\u0004\u0012\u00020\u000e0BH\u0002J\b\u0010C\u001a\u00020\u0007H\u0002J\b\u0010D\u001a\u00020\u0007H\u0002J\b\u0010E\u001a\u00020,H\u0002J\u0010\u0010F\u001a\u00020,2\u0006\u0010G\u001a\u00020\u001eH\u0002J\u0010\u0010H\u001a\u00020I2\u0006\u0010G\u001a\u00020\u001eH\u0002R\u0012\u0010\u0004\u001a\u00060\u0005R\u00020\u0000X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\f\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000e0\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0019\u0010\u000f\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000e0\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0014\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00150\u0014X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00150\u0017\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019R\u0014\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001b0\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001b0\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0012R\u000e\u0010\u001d\u001a\u00020\u001eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001f\u001a\u0004\u0018\u00010 X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010!\u001a\u00020\"X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020\"X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0012\u0010$\u001a\u0004\u0018\u00010%X\u0082\u000e\u00a2\u0006\u0004\n\u0002\u0010&R\u0014\u0010\'\u001a\b\u0012\u0004\u0012\u00020\u000e0(X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010)\u001a\u00020*X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006L"}, d2 = {"Lcom/pitwall/app/service/PitwallService;", "Landroidx/lifecycle/LifecycleService;", "<init>", "()V", "binder", "Lcom/pitwall/app/service/PitwallService$LocalBinder;", "track", "Lcom/pitwall/app/data/TrackMap;", "fusion", "Lcom/pitwall/app/fusion/SensorFusion;", "bridge", "Lcom/pitwall/app/service/BridgeClient;", "_telemetry", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/pitwall/app/data/TelemetryFrame;", "telemetry", "Lkotlinx/coroutines/flow/StateFlow;", "getTelemetry", "()Lkotlinx/coroutines/flow/StateFlow;", "_coaching", "Lkotlinx/coroutines/flow/MutableSharedFlow;", "Lcom/pitwall/app/data/CoachingMessage;", "coaching", "Lkotlinx/coroutines/flow/SharedFlow;", "getCoaching", "()Lkotlinx/coroutines/flow/SharedFlow;", "_isRunning", "", "isRunning", "driverLevel", "", "sessionJob", "Lkotlinx/coroutines/Job;", "frameCount", "", "lapCount", "bestLapTime", "", "Ljava/lang/Float;", "ringBuffer", "Lkotlin/collections/ArrayDeque;", "lastBurstAt", "", "onCreate", "", "onStartCommand", "intent", "Landroid/content/Intent;", "flags", "startId", "onBind", "Landroid/os/IBinder;", "initialise", "replayPath", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "processFrame", "frame", "(Lcom/pitwall/app/data/TelemetryFrame;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "setDriverLevel", "level", "stopSession", "getSessionStats", "", "", "serialiseBurst", "frames", "", "loadSonomaTrack", "stubSonomaTrack", "createNotificationChannel", "updateNotification", "text", "buildNotification", "Landroid/app/Notification;", "LocalBinder", "Companion", "app_debug"})
public final class PitwallService extends androidx.lifecycle.LifecycleService {
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.service.PitwallService.LocalBinder binder = null;
    private com.pitwall.app.data.TrackMap track;
    private com.pitwall.app.fusion.SensorFusion fusion;
    @org.jetbrains.annotations.NotNull()
    private final com.pitwall.app.service.BridgeClient bridge = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.pitwall.app.data.TelemetryFrame> _telemetry = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.pitwall.app.data.TelemetryFrame> telemetry = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableSharedFlow<com.pitwall.app.data.CoachingMessage> _coaching = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.SharedFlow<com.pitwall.app.data.CoachingMessage> coaching = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> _isRunning = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isRunning = null;
    @org.jetbrains.annotations.NotNull()
    private java.lang.String driverLevel = "intermediate";
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job sessionJob;
    private int frameCount = 0;
    private int lapCount = 0;
    @org.jetbrains.annotations.Nullable()
    private java.lang.Float bestLapTime;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.collections.ArrayDeque<com.pitwall.app.data.TelemetryFrame> ringBuffer = null;
    private long lastBurstAt = 0L;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_REPLAY_PATH = "replay_path";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_DRIVER_LEVEL = "driver_level";
    @org.jetbrains.annotations.NotNull()
    public static final com.pitwall.app.service.PitwallService.Companion Companion = null;
    
    public PitwallService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.pitwall.app.data.TelemetryFrame> getTelemetry() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.SharedFlow<com.pitwall.app.data.CoachingMessage> getCoaching() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isRunning() {
        return null;
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.os.IBinder onBind(@org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
        return null;
    }
    
    private final java.lang.Object initialise(java.lang.String replayPath, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object processFrame(com.pitwall.app.data.TelemetryFrame frame, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void setDriverLevel(@org.jetbrains.annotations.NotNull()
    java.lang.String level) {
    }
    
    public final void stopSession() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.lang.String, java.lang.Object> getSessionStats() {
        return null;
    }
    
    private final java.lang.String serialiseBurst(java.util.List<com.pitwall.app.data.TelemetryFrame> frames) {
        return null;
    }
    
    private final com.pitwall.app.data.TrackMap loadSonomaTrack() {
        return null;
    }
    
    private final com.pitwall.app.data.TrackMap stubSonomaTrack() {
        return null;
    }
    
    private final void createNotificationChannel() {
    }
    
    private final void updateNotification(java.lang.String text) {
    }
    
    private final android.app.Notification buildNotification(java.lang.String text) {
        return null;
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0007"}, d2 = {"Lcom/pitwall/app/service/PitwallService$Companion;", "", "<init>", "()V", "EXTRA_REPLAY_PATH", "", "EXTRA_DRIVER_LEVEL", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0004\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u0011\u0010\u0004\u001a\u00020\u00058F\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\b"}, d2 = {"Lcom/pitwall/app/service/PitwallService$LocalBinder;", "Landroid/os/Binder;", "<init>", "(Lcom/pitwall/app/service/PitwallService;)V", "service", "Lcom/pitwall/app/service/PitwallService;", "getService", "()Lcom/pitwall/app/service/PitwallService;", "app_debug"})
    public final class LocalBinder extends android.os.Binder {
        
        public LocalBinder() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pitwall.app.service.PitwallService getService() {
            return null;
        }
    }
}
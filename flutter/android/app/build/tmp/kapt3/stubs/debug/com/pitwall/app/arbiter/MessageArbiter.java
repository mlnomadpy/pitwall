package com.pitwall.app.arbiter;

/**
 * Message Arbiter — coordinates all coaching output from both paths.
 *
 * Rules (from coaching-engine.md):
 *  P3 Safety     → deliver immediately, interrupt everything
 *  P2 Technique  → hold until |gLat| < 0.3G (on straight), max wait 5s
 *  P1 Strategy   → queue behind P2
 *  Conflict      → same corner from both paths: higher priority wins; tie → hot path wins
 *  Cooldown      → 3s minimum between deliveries
 *  Stale expiry  → drop messages > 5s in queue
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u000e\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0006J\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0015\u001a\u00020\u0016J\u0006\u0010\u0017\u001a\u00020\u0012R\u0014\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00060\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00060\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0018"}, d2 = {"Lcom/pitwall/app/arbiter/MessageArbiter;", "", "<init>", "()V", "queue", "", "Lcom/pitwall/app/data/CoachingMessage;", "lastDeliveredAt", "", "lastDeliveredCorner", "", "_delivered", "Lkotlinx/coroutines/flow/MutableSharedFlow;", "delivered", "Lkotlinx/coroutines/flow/SharedFlow;", "getDelivered", "()Lkotlinx/coroutines/flow/SharedFlow;", "submit", "", "message", "evaluate", "frame", "Lcom/pitwall/app/data/TelemetryFrame;", "reset", "app_debug"})
public final class MessageArbiter {
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.pitwall.app.data.CoachingMessage> queue = null;
    private long lastDeliveredAt = 0L;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String lastDeliveredCorner;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableSharedFlow<com.pitwall.app.data.CoachingMessage> _delivered = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.SharedFlow<com.pitwall.app.data.CoachingMessage> delivered = null;
    
    public MessageArbiter() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.SharedFlow<com.pitwall.app.data.CoachingMessage> getDelivered() {
        return null;
    }
    
    /**
     * Submit a message from either hot path or warm path.
     * Thread-safe — call from any coroutine.
     */
    @kotlin.jvm.Synchronized()
    public final synchronized void submit(@org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.CoachingMessage message) {
    }
    
    /**
     * Evaluate the queue against the current telemetry frame.
     * Called every frame (10Hz) by PitwallService.
     * Returns a message to deliver, or null.
     */
    @kotlin.jvm.Synchronized()
    @org.jetbrains.annotations.Nullable()
    public final synchronized com.pitwall.app.data.CoachingMessage evaluate(@org.jetbrains.annotations.NotNull()
    com.pitwall.app.data.TelemetryFrame frame) {
        return null;
    }
    
    @kotlin.jvm.Synchronized()
    public final synchronized void reset() {
    }
}
package com.pitwall.app.service;

/**
 * Thin HTTP client for the local Python pitwall_bridge.py server.
 * Reachable via `adb reverse tcp:8765 tcp:8765`.
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\u0018\u00002\u00020\u0001B\u0011\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0004\u0010\u0005J\u0018\u0010\b\u001a\u0004\u0018\u00010\u00032\u0006\u0010\t\u001a\u00020\u0003H\u0086@\u00a2\u0006\u0002\u0010\nJ\u0010\u0010\u000b\u001a\u0004\u0018\u00010\u0003H\u0086@\u00a2\u0006\u0002\u0010\fJ\u000e\u0010\r\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010\fR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/pitwall/app/service/BridgeClient;", "", "baseUrl", "", "<init>", "(Ljava/lang/String;)V", "http", "Lokhttp3/OkHttpClient;", "analyze", "burstJson", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getInsightsJson", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "isReachable", "", "app_debug"})
public final class BridgeClient {
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String baseUrl = null;
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient http = null;
    
    public BridgeClient(@org.jetbrains.annotations.NotNull()
    java.lang.String baseUrl) {
        super();
    }
    
    /**
     * POST telemetry burst JSON to /analyze. Returns coaching text or null.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object analyze(@org.jetbrains.annotations.NotNull()
    java.lang.String burstJson, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    /**
     * GET /insights — returns raw JSON string or null.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getInsightsJson(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    /**
     * GET /health — returns true if bridge is reachable.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object isReachable(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    public BridgeClient() {
        super();
    }
}
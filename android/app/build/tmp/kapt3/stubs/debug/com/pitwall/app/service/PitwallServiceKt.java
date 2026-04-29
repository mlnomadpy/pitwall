package com.pitwall.app.service;

@kotlin.Metadata(mv = {2, 2, 0}, k = 2, xi = 48, d1 = {"\u0000\u0018\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082T\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0002\u001a\u00020\u0001X\u0082T\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\b"}, d2 = {"TAG", "", "NOTIF_CHANNEL", "NOTIF_ID", "", "BURST_INTERVAL_MS", "", "RING_BUFFER_MAX", "app_debug"})
public final class PitwallServiceKt {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "PitwallService";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String NOTIF_CHANNEL = "pitwall_session";
    private static final int NOTIF_ID = 1001;
    private static final long BURST_INTERVAL_MS = 5000L;
    private static final int RING_BUFFER_MAX = 50;
}
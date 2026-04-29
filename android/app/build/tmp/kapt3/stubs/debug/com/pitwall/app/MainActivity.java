package com.pitwall.app;

/**
 * MainActivity — thin Compose host + service lifecycle owner.
 *
 * The ViewModel emits [SessionCommand] events; this Activity executes them
 * using Activity context (required for foreground service on API 31+).
 * The ViewModel never touches Context or Service directly.
 */
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0012\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012H\u0014J\u001a\u0010\u0013\u001a\u00020\u00102\b\u0010\u0014\u001a\u0004\u0018\u00010\u00152\u0006\u0010\u0016\u001a\u00020\u0015H\u0002J\b\u0010\u0017\u001a\u00020\u0010H\u0002J\b\u0010\u0018\u001a\u00020\u0010H\u0014R\u001b\u0010\u0004\u001a\u00020\u00058BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\b\u0010\t\u001a\u0004\b\u0006\u0010\u0007R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u000e\u00a8\u0006\u0019"}, d2 = {"Lcom/pitwall/app/MainActivity;", "Landroidx/activity/ComponentActivity;", "<init>", "()V", "viewModel", "Lcom/pitwall/app/ui/PitwallViewModel;", "getViewModel", "()Lcom/pitwall/app/ui/PitwallViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "isBound", "", "serviceConnection", "Landroid/content/ServiceConnection;", "Landroid/content/ServiceConnection;", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "startAndBindService", "replayPath", "", "level", "stopAndUnbindService", "onDestroy", "app_debug"})
public final class MainActivity extends androidx.activity.ComponentActivity {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    private boolean isBound = false;
    @org.jetbrains.annotations.NotNull()
    private final android.content.ServiceConnection serviceConnection = null;
    
    public MainActivity() {
        super(0);
    }
    
    private final com.pitwall.app.ui.PitwallViewModel getViewModel() {
        return null;
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void startAndBindService(java.lang.String replayPath, java.lang.String level) {
    }
    
    private final void stopAndUnbindService() {
    }
    
    @java.lang.Override()
    protected void onDestroy() {
    }
}
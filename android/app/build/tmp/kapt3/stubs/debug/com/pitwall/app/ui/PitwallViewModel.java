package com.pitwall.app.ui;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u000f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0004\u0010\u0005J\u000e\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u0015J\u0006\u0010\u001c\u001a\u00020\u001aJ\u0018\u0010\u001d\u001a\u00020\u001a2\b\u0010\u001e\u001a\u0004\u0018\u00010\u001f2\u0006\u0010 \u001a\u00020\u001fJ\u0006\u0010!\u001a\u00020\u001aJ\u0006\u0010\"\u001a\u00020\u001aJ\u0006\u0010#\u001a\u00020\u001aJ\u0006\u0010$\u001a\u00020\u001aJ\u000e\u0010%\u001a\u00020\u001a2\u0006\u0010 \u001a\u00020\u001fJ\u0010\u0010&\u001a\u00020\u001a2\u0006\u0010\'\u001a\u00020(H\u0002J\b\u0010)\u001a\u00020\u001aH\u0014R\u0014\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\t\u001a\b\u0012\u0004\u0012\u00020\b0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0017X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0018\u001a\u0004\u0018\u00010\u0017X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006*"}, d2 = {"Lcom/pitwall/app/ui/PitwallViewModel;", "Landroidx/lifecycle/AndroidViewModel;", "application", "Landroid/app/Application;", "<init>", "(Landroid/app/Application;)V", "_ui", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/pitwall/app/ui/PitwallUiState;", "ui", "Lkotlinx/coroutines/flow/StateFlow;", "getUi", "()Lkotlinx/coroutines/flow/StateFlow;", "_commands", "Lkotlinx/coroutines/flow/MutableSharedFlow;", "Lcom/pitwall/app/ui/SessionCommand;", "commands", "Lkotlinx/coroutines/flow/SharedFlow;", "getCommands", "()Lkotlinx/coroutines/flow/SharedFlow;", "service", "Lcom/pitwall/app/service/PitwallService;", "telemetryJob", "Lkotlinx/coroutines/Job;", "coachingJob", "onServiceConnected", "", "svc", "onServiceDisconnected", "startSession", "replayPath", "", "level", "onSessionStarted", "stopSession", "enterPaddock", "returnToTrack", "setDriverLevel", "checkAutoTransition", "frame", "Lcom/pitwall/app/data/TelemetryFrame;", "onCleared", "app_debug"})
public final class PitwallViewModel extends androidx.lifecycle.AndroidViewModel {
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.pitwall.app.ui.PitwallUiState> _ui = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.pitwall.app.ui.PitwallUiState> ui = null;
    
    /**
     * MainActivity collects this to execute Activity-context service calls.
     */
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableSharedFlow<com.pitwall.app.ui.SessionCommand> _commands = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.SharedFlow<com.pitwall.app.ui.SessionCommand> commands = null;
    @org.jetbrains.annotations.Nullable()
    private com.pitwall.app.service.PitwallService service;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job telemetryJob;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job coachingJob;
    
    public PitwallViewModel(@org.jetbrains.annotations.NotNull()
    android.app.Application application) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.pitwall.app.ui.PitwallUiState> getUi() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.SharedFlow<com.pitwall.app.ui.SessionCommand> getCommands() {
        return null;
    }
    
    public final void onServiceConnected(@org.jetbrains.annotations.NotNull()
    com.pitwall.app.service.PitwallService svc) {
    }
    
    public final void onServiceDisconnected() {
    }
    
    public final void startSession(@org.jetbrains.annotations.Nullable()
    java.lang.String replayPath, @org.jetbrains.annotations.NotNull()
    java.lang.String level) {
    }
    
    public final void onSessionStarted() {
    }
    
    public final void stopSession() {
    }
    
    public final void enterPaddock() {
    }
    
    public final void returnToTrack() {
    }
    
    public final void setDriverLevel(@org.jetbrains.annotations.NotNull()
    java.lang.String level) {
    }
    
    private final void checkAutoTransition(com.pitwall.app.data.TelemetryFrame frame) {
    }
    
    @java.lang.Override()
    protected void onCleared() {
    }
}
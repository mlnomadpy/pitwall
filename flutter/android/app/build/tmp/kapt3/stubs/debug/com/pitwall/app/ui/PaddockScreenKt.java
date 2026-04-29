package com.pitwall.app.ui;

@kotlin.Metadata(mv = {2, 2, 0}, k = 2, xi = 48, d1 = {"\u0000<\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\u001a*\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u00062\b\u0010\u0007\u001a\u0004\u0018\u00010\b2\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00040\nH\u0007\u001a\b\u0010\u000b\u001a\u00020\u0004H\u0003\u001a1\u0010\f\u001a\u00020\u00042\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u000e2\u0006\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\u0012\u001a\u00020\u0013H\u0003\u00a2\u0006\u0004\b\u0014\u0010\u0015\u001a\u0012\u0010\u0016\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u0003\u001a\b\u0010\u0017\u001a\u00020\u0004H\u0003\u001a\u0012\u0010\u0018\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u0003\u001a\b\u0010\u0019\u001a\u00020\u0004H\u0003\u001a\u0016\u0010\u001a\u001a\u00020\u00042\f\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u000e0\u0001H\u0003\"\u0014\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001c"}, d2 = {"MOCK_LAPS", "", "Lcom/pitwall/app/ui/LapRow;", "PaddockScreen", "", "telemetry", "Lcom/pitwall/app/data/TelemetryFrame;", "lastCoaching", "Lcom/pitwall/app/data/CoachingMessage;", "onReturnToTrack", "Lkotlin/Function0;", "LapsTab", "StatCard", "label", "", "value", "modifier", "Landroidx/compose/ui/Modifier;", "valueColor", "Landroidx/compose/ui/graphics/Color;", "StatCard-g2O1Hgs", "(Ljava/lang/String;Ljava/lang/String;Landroidx/compose/ui/Modifier;J)V", "SpeedTraceTab", "CornersTab", "FrictionTab", "ProfileTab", "AiDebriefTab", "messages", "app_debug"})
public final class PaddockScreenKt {
    @org.jetbrains.annotations.NotNull()
    private static final java.util.List<com.pitwall.app.ui.LapRow> MOCK_LAPS = null;
    
    @androidx.compose.runtime.Composable()
    public static final void PaddockScreen(@org.jetbrains.annotations.Nullable()
    com.pitwall.app.data.TelemetryFrame telemetry, @org.jetbrains.annotations.Nullable()
    com.pitwall.app.data.CoachingMessage lastCoaching, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onReturnToTrack) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void LapsTab() {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void SpeedTraceTab(com.pitwall.app.data.TelemetryFrame telemetry) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void CornersTab() {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void FrictionTab(com.pitwall.app.data.TelemetryFrame telemetry) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ProfileTab() {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void AiDebriefTab(java.util.List<java.lang.String> messages) {
    }
}
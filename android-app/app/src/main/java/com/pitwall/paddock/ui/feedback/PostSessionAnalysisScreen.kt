package com.pitwall.paddock.ui.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.PostSessionAnalysisCatalog
import com.pitwall.paddock.data.PostSessionGroup
import com.pitwall.paddock.data.PostSessionModule
import com.pitwall.paddock.ui.feedback.visuals.FeedbackDataVisualsSection
import com.pitwall.paddock.ui.theme.CardStroke
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostSessionAnalysisScreen(
    onBack: () -> Unit,
    onOpenHistoricalMap: () -> Unit = {},
) {
    var openGroups by remember { mutableStateOf(setOf("1")) }
    var openModules by remember { mutableStateOf(setOf<String>()) }
    fun toggleGroup(id: String) {
        openGroups = if (id in openGroups) openGroups - id else openGroups + id
    }
    fun toggleModule(id: String) {
        openModules = if (id in openModules) openModules - id else openModules + id
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(PitwallBg)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text(
                "POST-RACE / SESSION FEEDBACK",
                color = PitwallCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "PITWALL-STYLE CATALOG (STATS & MODULES)",
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
        )
        OutlinedButton(
            onClick = onOpenHistoricalMap,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            Text(
                "HISTORICAL TRACK MAP (SPEED + DRS + ELEVATION)",
                color = PitwallCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FeedbackDataVisualsSection()
            }
            item {
                HorizontalDivider(
                    color = CardStroke,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                Text(
                    "TEXT MODULES (CATALOG)",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                )
            }
            items(PostSessionAnalysisCatalog.groups, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    isOpen = openGroups.contains(group.id),
                    onToggleGroup = { toggleGroup(group.id) },
                    openModuleIds = openModules,
                    onToggleModule = { toggleModule(it) },
                )
            }
            item { Spacer(Modifier.size(32.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(
    group: PostSessionGroup,
    isOpen: Boolean,
    onToggleGroup: () -> Unit,
    openModuleIds: Set<String>,
    onToggleModule: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PitwallSurface, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggleGroup() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                group.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = PitwallCyan,
            )
        }
        Text(
            group.blurb,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = CardStroke)
                group.modules.forEach { mod ->
                    ModuleRow(
                        mod = mod,
                        isOpen = openModuleIds.contains(mod.id),
                        onToggle = { onToggleModule(mod.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleRow(
    mod: PostSessionModule,
    isOpen: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PitwallBg, RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                mod.title,
                color = PitwallCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 0.dp)) {
                Text(
                    mod.body,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (mod.statHints.isNotEmpty()) {
                    Text(
                        "Suggested stats to wire (API)",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        mod.statHints.forEach { hint ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PitwallCyan.copy(alpha = 0.12f),
                                modifier = Modifier.padding(0.dp),
                            ) {
                                Text(
                                    hint,
                                    color = PitwallCyan,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

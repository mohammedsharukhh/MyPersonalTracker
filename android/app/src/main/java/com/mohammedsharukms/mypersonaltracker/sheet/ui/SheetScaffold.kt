package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Opens More (summaries, tools) — the ⋯ button on every tab's top bar. */
val LocalOpenMore = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }
/** Opens Settings, from the gear on every tab. */
val LocalOpenSettings = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * The frame every sheet screen shares: a large collapsing title on the page
 * colour, pull to refresh, and a padded, spaced list of cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScreen(
    title: String,
    isRefreshing: Boolean,
    onRefresh: suspend () -> Unit,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    bottomPadding: Int = 24,
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection).background(SheetTheme.pageWash),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    actions()
                    // On the tabs: More (summaries, tools), then Settings on its own.
                    val openMore = LocalOpenMore.current
                    val openSettings = LocalOpenSettings.current
                    if (onBack == null && openMore != null) IconButton(onClick = openMore) {
                        Icon(androidx.compose.material.icons.Icons.Outlined.MoreHoriz, contentDescription = "More", tint = SheetTheme.navy)
                    }
                    if (onBack == null && openSettings != null) IconButton(onClick = openSettings) {
                        Icon(androidx.compose.material.icons.Icons.Outlined.Settings, contentDescription = "Settings", tint = SheetTheme.navy)
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    // Clear over the colour wash; solid once the page scrolls under it.
                    containerColor = Color.Transparent,
                    scrolledContainerColor = SheetTheme.page,
                    titleContentColor = SheetTheme.text,
                ),
            )
        },
        floatingActionButton = floating,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { onRefresh() } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** The error line with a working Retry. */
@Composable
fun RetryBanner(store: com.mohammedsharukms.mypersonaltracker.sheet.SheetStore, message: String) {
    val scope = rememberCoroutineScope()
    SheetErrorBanner(message) { scope.launch { store.refresh() } }
}

/**
 * A full-height bottom sheet with Cancel · Title · Save across the top, the
 * Android counterpart of the iOS editor sheets. Errors show inline under the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(
    title: String,
    onDismiss: () -> Unit,
    saveLabel: String = "Save",
    cancelLabel: String = "Cancel",
    canSave: Boolean = true,
    saving: Boolean = false,
    error: String? = null,
    onSave: (() -> Unit)?,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetTheme.page,
        dragHandle = null,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().imePadding()) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                SmallTextButton(cancelLabel, onClick = onDismiss)
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = SheetTheme.text,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                if (onSave == null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(72.dp))
                } else if (saving) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.padding(horizontal = 24.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onSave, enabled = canSave) {
                        Text(saveLabel, fontWeight = FontWeight.Bold, color = if (canSave) SheetTheme.navy else SheetTheme.tertiary)
                    }
                }
            }
            if (error != null) {
                androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { SheetErrorBanner(error) {} }
            }
            androidx.compose.foundation.layout.Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
            if (bottomBar != null) bottomBar()
        }
    }
}

package com.aicanvas.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Spring
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.data.Element
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AppViewModel) {
    AICanvasTheme(vm.themeMode, vm.accent, vm.textSize) { Home(vm) }
}

@Composable
private fun Home(vm: AppViewModel) {
    BackHandler(vm.chatOpen || vm.menuOpen) {
        when {
            vm.menuOpen && vm.menuPage != "main" && !vm.menuDirect -> vm.menuPage = "main"
            else -> { vm.chatOpen = false; vm.closeMenu() }
        }
    }

    // Ask for notification permission the first time the AI sets a reminder or a timer runs.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) vm.notificationsDenied() }
    LaunchedEffect(vm.wantsNotifications) {
        if (vm.wantsNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.wantsNotifications = false
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.calendarAllowed(it) }
    LaunchedEffect(vm.wantsCalendarPermission) {
        if (vm.wantsCalendarPermission) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }

    // Speak instead of typing: the phone's own speech recogniser fills in the message and sends it.
    val context = LocalContext.current
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) {
            vm.draft = heard
            vm.openChat()
            vm.send()
        }
    }
    val listen = {
        if (!vm.settings.isConfigured) vm.openSettings() else try {
            voice.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell me anything")
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Voice input isn't available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(vm.wantsVoice) {
        if (vm.wantsVoice) {
            vm.wantsVoice = false
            listen()
        }
    }

    // The undo pill goes away by itself after a few seconds.
    LaunchedEffect(vm.undo) {
        if (vm.undo != null) { delay(5000); vm.dismissUndo() }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Elements(vm)

        val idle = !vm.chatOpen && !vm.menuOpen
        var quick by remember { mutableStateOf(false) }
        RoundButton(Icons.Rounded.Menu, "Menu", idle, Alignment.BottomStart, onClick = { quick = true })
        // The menu button opens a small popup of pages (Tasks, Habits, …, Settings) above it.
        Box(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, bottom = 84.dp)) {
            QuickMenu(vm, quick) { quick = false }
        }
        RoundButton(Icons.Rounded.ChatBubbleOutline, "Chat", idle, Alignment.BottomEnd, primary = true, onLongClick = listen, onClick = { vm.openChat() })

        AnimatedVisibility(
            vm.undo != null && idle,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
        ) {
            Row(
                Modifier.clip(CircleShape).background(C.Accent).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(vm.undo?.label.orEmpty(), color = C.OnAccent, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Undo",
                    Modifier.clip(CircleShape).clickable { vm.undoLast() }.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = C.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Tapping anywhere outside the chat box closes it; the cards stay visible behind.
        if (vm.chatOpen) {
            Box(Modifier.fillMaxSize().onTap { vm.chatOpen = false })
        }
        AnimatedVisibility(
            vm.chatOpen,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) { ChatBox(vm, listen) }

        // Settings is a full screen that slides in from the side, with a back arrow (and the system back gesture).
        AnimatedVisibility(
            vm.menuOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) { MenuSheet(vm) }

        CanvasDialogs(vm)
    }
}

/**
 * Cards in rows, most important first. Each card is as wide as the AI decided (within what its content needs),
 * and cards sharing a row are the same height. Long-press and drag a card onto another to swap places; long-press
 * without moving opens its menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Elements(vm: AppViewModel) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Re-pack only when the cards change, not on every drag frame or recomposition.
    val rows by remember { derivedStateOf { packRows(vm.visibleElements) } }
    // Where every visible card is on screen, for dropping a dragged card onto another.
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf<String?>(null) }
    // Where the dragged card's top-left was when the drag began, and how far the finger has moved since.
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    // When the AI adds something, bring it into view and let it glow for a moment (not on first launch).
    val newest = vm.visibleElements.maxByOrNull { it.createdAt }?.id
    var seenNewest by remember { mutableStateOf(newest) }
    var fresh by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(newest) {
        if (newest != null && newest != seenNewest) {
            seenNewest = newest
            fresh = newest
            val index = rows.indexOfFirst { row -> row.any { it.first.id == newest } }
            if (index >= 0) list.animateScrollToItem(index)
            delay(2500)
            fresh = null
        }
    }

    // Pull down to have the AI tidy up and refresh live cards (weather, news).
    PullToRefreshBox(
        isRefreshing = vm.tidying,
        onRefresh = { vm.tidyNow() },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().onGloballyPositioned { listBounds = it.boundsInRoot() },
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { HeaderBar(Modifier.animateItem()) }
            item(key = "canvases") { CanvasBar(vm, Modifier.animateItem()) }
            if (vm.tidying) {
                item(key = "tidying") {
                    Text("Updating your screen…", Modifier.animateItem().padding(start = 6.dp), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            // A soft placeholder while the AI designs something new from the chat.
            if (vm.busy && vm.workingOn == null && !vm.tidying) {
                item(key = "designing") { DesigningCard(vm.status, Modifier.animateItem()) }
            }
            if (vm.visibleElements.isEmpty() && !vm.busy && !vm.tidying) {
                item(key = "empty") { EmptyState(vm, Modifier.animateItem()) }
            }
            items(rows, key = { row -> row.joinToString("+") { it.first.id } }) { row ->
                val holdsDragged = row.any { it.first.id == dragging }
                EqualHeightRow(
                    spans = row.map { it.second },
                    gap = 12.dp,
                    modifier = Modifier
                        .then(
                            if (holdsDragged) Modifier.zIndex(1f)
                            else Modifier.animateItem(
                                fadeInSpec = tween(320),
                                placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                                fadeOutSpec = tween(180),
                            )
                        )
                        .fillMaxWidth(),
                ) {
                    row.forEach { (e, _) ->
                        val isDragging = dragging == e.id
                        DisposableEffect(e.id) { onDispose { bounds.remove(e.id) } }
                        ElementCard(
                            e, vm,
                            modifier = Modifier
                                .onGloballyPositioned { bounds[e.id] = it.boundsInRoot() }
                                .then(
                                    if (isDragging) Modifier.zIndex(1f).graphicsLayer {
                                        // Follow the finger, even if the list scrolls underneath.
                                        val shift = dragOrigin + dragDelta - (bounds[e.id]?.topLeft ?: dragOrigin)
                                        translationX = shift.x
                                        translationY = shift.y
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                        shadowElevation = 24f
                                        shape = RoundedCornerShape(20.dp)
                                        clip = true
                                    } else Modifier
                                )
                                .pointerInput(e.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragging = e.id
                                            dragOrigin = bounds[e.id]?.topLeft ?: Offset.Zero
                                            dragDelta = Offset.Zero
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDelta += amount
                                            val center = dragOrigin + dragDelta + Offset(size.width / 2f, size.height / 2f)
                                            val over = bounds.entries.firstOrNull { it.key != e.id && it.value.contains(center) }?.key
                                            if (over != hover) {
                                                hover = over
                                                if (over != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            // Near the top or bottom edge, scroll so cards can travel the whole screen.
                                            val edge = 160f
                                            when {
                                                center.y < listBounds.top + edge -> scope.launch { list.scrollBy(-24f) }
                                                center.y > listBounds.bottom - edge -> scope.launch { list.scrollBy(24f) }
                                            }
                                        },
                                        onDragEnd = {
                                            val target = hover
                                            if (dragDelta.getDistance() < 24f) menuFor = e.id
                                            else if (target != null) {
                                                vm.moveOnto(e.id, target)
                                                vm.finishMove(e.id)
                                            }
                                            dragging = null
                                            hover = null
                                        },
                                        onDragCancel = { dragging = null; hover = null },
                                    )
                                },
                            highlight = hover == e.id,
                            fresh = fresh == e.id,
                            menuOpen = menuFor == e.id,
                            onMenuDismiss = { menuFor = null },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cards side by side at the same height. Each card is first measured at its natural height (in a measuring pass
 * that is never shown), then all of them are laid out at the tallest one's height. Estimating heights instead
 * gets wrapped lines wrong and squashes the bottom of a card.
 */
@Composable
private fun EqualHeightRow(spans: List<Int>, gap: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    SubcomposeLayout(modifier) { constraints ->
        val gapPx = gap.roundToPx()
        val free = constraints.maxWidth - gapPx * (spans.size - 1)
        // A row that isn't full (a lone tile) keeps its share of a full row instead of stretching.
        val widths = if (spans.sum() >= 6) spans.map { free * it / spans.sum() }
        else spans.map { (constraints.maxWidth - gapPx) * it / 6 }
        // A card alone in its row takes its own natural height. Measuring it in a separate pass would freeze it at
        // the height of a copy whose in-card state (an opened timetable, a picked day) never changes.
        if (spans.size == 1) {
            val p = subcompose("place", content).map { it.measure(Constraints(minWidth = widths[0], maxWidth = widths[0])) }
            val h = p.maxOfOrNull { it.height } ?: 0
            return@SubcomposeLayout layout(constraints.maxWidth, h) { p.forEach { it.placeRelative(0, 0) } }
        }
        val tallest = subcompose("measure", content)
            .mapIndexed { i, m -> m.measure(Constraints(minWidth = widths[i], maxWidth = widths[i])).height }
            .maxOrNull() ?: 0
        val placeables = subcompose("place", content).mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], tallest)) }
        layout(constraints.maxWidth, tallest) {
            var x = 0
            placeables.forEachIndexed { i, p ->
                p.placeRelative(x, 0)
                x += widths[i] + gapPx
            }
        }
    }
}

@Composable
private fun ElementCard(
    e: Element,
    vm: AppViewModel,
    modifier: Modifier,
    highlight: Boolean,
    fresh: Boolean,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // The card's drawing is recorded here too, so "Share as image" can turn it into a picture.
    val picture = rememberGraphicsLayer()
    val actions = remember(e.id) {
        RenderActions(
            onUpdate = { path, change -> vm.updateNode(e.id, path, change) },
            onPress = { label, prompt -> vm.press(e.id, label, prompt) },
            onOpen = { uri -> openOnPhone(context, uri) },
            onTimer = { path, endsAt, left, label -> vm.setTimer(e.id, path, endsAt, left, label) },
        )
    }
    // Swipe a card sideways to delete it. Past a quarter of its width it stays pushed aside and uncovers a red
    // Delete button, which confirms; tapping the card slides it back. A short swipe just springs back.
    var swipe by remember { mutableFloatStateOf(0f) }
    var swiping by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val reveal = with(LocalDensity.current) { 112.dp.toPx() }
    val shown by animateFloatAsState(swipe, if (swiping) snap() else spring(), label = "swipe")
    val cancel = { armed = false; swipe = 0f }
    val glow by animateFloatAsState(if (fresh) 1f else 0f, tween(700), label = "glow")
    // A card whose to-dos are all ticked fades back: it's done.
    val allDone = remember(e.ui) { allTicked(e.ui) }
    // Big cards fold to a short summary so a full screen stays easy to scan; small ones are shown whole.
    val foldable = remember(e.ui) { !com.aicanvas.app.data.CardSummary.isSmall(e.ui) }
    val corner = 20.dp
    Box(
        // The swipe goes outside the long-press drag carried in [modifier], so the drag sees the finger first and a
        // long-pressed card dragged sideways moves instead of swiping.
        Modifier.pointerInput(e.id) {
            detectHorizontalDragGestures(
                onDragStart = { swiping = true },
                onDragEnd = {
                    swiping = false
                    if (abs(swipe) > size.width * 0.25f) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        armed = true
                        swipe = if (swipe < 0) -reveal else reveal
                    } else cancel()
                },
                onDragCancel = { swiping = false; cancel() },
            ) { change, amount ->
                change.consume()
                swipe += amount
            }
        }.then(modifier)
    ) {
        if (shown != 0f) {
            Box(Modifier.matchParentSize()) {
                Text(
                    "Delete",
                    Modifier
                        .align(if (shown < 0) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 12.dp)
                        .clip(CircleShape)
                        .background(C.Bad)
                        .clickable(enabled = armed) { cancel(); vm.remove(e.id) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = shown
                    alpha = if (allDone) 0.5f else 1f
                }
                .drawWithContent {
                    picture.record { this@drawWithContent.drawContent() }
                    drawLayer(picture)
                }
                .clip(RoundedCornerShape(corner))
                .background(toneBackground(e.tone))
                // A plain 1dp outline keeps cards crisp on any background.
                .border(1.dp, C.Line, RoundedCornerShape(corner))
                // The card a dragged card would land on, or a soft glow on a card the AI just made.
                .then(
                    when {
                        highlight -> Modifier.border(2.dp, C.Accent, RoundedCornerShape(20.dp))
                        glow > 0f -> Modifier.border(2.dp, C.Accent.copy(alpha = 0.5f * glow), RoundedCornerShape(20.dp))
                        else -> Modifier
                    }
                )
                // A quick tap on the card (not on a button or tick inside it) folds or unfolds it. A long press or a
                // swipe is not a tap, so the menu and the drag keep working.
                .then(if (foldable) Modifier.pointerInput(e.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null && !up.isConsumed && up.uptimeMillis - down.uptimeMillis < 400) vm.toggleExpanded(e.id)
                    }
                } else Modifier)
                .animateContentSize(tween(280))
                .padding(18.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalInk provides (Tones.ink(e.tone) ?: Color.Unspecified)) {
                val module = remember(e.ui) { com.aicanvas.app.data.Modules.kindOf(e.ui) }
                if (module != null) ModuleCard(module, vm)
                else if (foldable && !e.expanded) FoldedCard(e) else Column(Modifier.fillMaxWidth()) {
                    UiNode(e.ui, actions, modifier = Modifier.fillMaxWidth())
                    LiveFooter(e, vm)
                }
            }
        }
        // While the Delete button shows, a tap on the card cancels instead of reaching what's inside it.
        if (armed) Box(Modifier.matchParentSize().graphicsLayer { translationX = shown }.onTap { cancel() })
        if (vm.workingOn == e.id) {
            CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(14.dp).size(16.dp), color = C.Muted, strokeWidth = 2.dp)
        }
        DropdownMenu(menuOpen, onMenuDismiss, shape = RoundedCornerShape(16.dp), containerColor = if (C.dark) C.Raised else C.Bg) {
            DropdownMenuItem(text = { Text("Change with AI") }, onClick = { onMenuDismiss(); vm.changeWithAi(e.id) })
            DropdownMenuItem(text = { Text("Move to top") }, onClick = { onMenuDismiss(); vm.moveToTop(e.id) })
            DropdownMenuItem(text = { Text("Move up") }, onClick = { onMenuDismiss(); vm.moveBy(e.id, -1) })
            DropdownMenuItem(text = { Text("Move down") }, onClick = { onMenuDismiss(); vm.moveBy(e.id, 1) })
            vm.canvases.filter { it.id != e.canvas }.forEach { c ->
                DropdownMenuItem(text = { Text("Move to ${c.name}") }, onClick = { onMenuDismiss(); vm.moveToCanvas(e.id, c.id) })
            }
            DropdownMenuItem(
                text = { Text("Share as image") },
                onClick = {
                    onMenuDismiss()
                    scope.launch { shareImage(context, picture.toImageBitmap().asAndroidBitmap()) }
                },
            )
            if (e.placed) DropdownMenuItem(text = { Text("Let AI place it") }, onClick = { onMenuDismiss(); vm.letAiPlace(e.id) })
            DropdownMenuItem(text = { Text("Delete", color = C.Bad) }, onClick = { onMenuDismiss(); vm.remove(e.id) })
        }
    }
}

/** A folded card: a portrait tile (about 3:4) with a big emoji, the title, one or two key lines and an arrow. */
@Composable
private fun FoldedCard(e: Element) {
    val folded = remember(e.ui) { com.aicanvas.app.data.CardSummary.fold(e.ui) }
    val (emoji, title) = remember(folded.title) { com.aicanvas.app.data.CardSummary.splitEmoji(folded.title) }
    // A tile alone on its row is stretched to full width; there it's a short wide strip, not a tall portrait.
    androidx.compose.foundation.layout.BoxWithConstraints { val wide = maxWidth > 280.dp
    Column(
        (if (wide) Modifier.fillMaxWidth().heightIn(min = 96.dp) else Modifier.fillMaxWidth().aspectRatio(0.82f).heightIn(min = 112.dp)).semantics { stateDescription = "Folded. Tap to open." },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Only an emoji the card's own title carries is shown; no stock icon.
        if (emoji.isNotEmpty()) Text(emoji, fontSize = 26.sp)
        Text(title, color = Tones.ink(e.tone) ?: C.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        folded.lines.forEach {
            Text(it, color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.align(Alignment.End), tint = C.Muted)
    }
    }
}

/** First launch / nothing on screen: say what the app does and offer a few one-tap starters. */
@Composable
private fun EmptyState(vm: AppViewModel, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Your screen is empty", color = C.Text, style = MaterialTheme.typography.titleLarge)
        Text(
            if (vm.settings.isConfigured) "Tell the AI what you want to keep track of and it builds a card for it. Tap any idea to start:"
            else "First, open the menu (bottom left) and add an API key. Then ask for anything — a to-do list, a timer, a habit tracker.",
            color = C.Muted,
            fontSize = 14.sp,
        )
        if (vm.settings.isConfigured) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.aicanvas.app.data.Starters.ALL.forEach { s ->
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Tones.tint(s.tone))
                            .clickable { vm.useStarter(s) }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.emoji, fontSize = 15.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(s.title, color = Tones.ink(s.tone) ?: C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            Text(
                "Open settings",
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.openSettings() }.padding(vertical = 8.dp, horizontal = 4.dp),
                color = C.Accent,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun DesigningCard(status: String, modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "designing")
    val alpha by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(20.dp))
            .background(C.Card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.45f).height(16.dp).clip(CircleShape).background(C.Muted.copy(alpha = 0.22f)))
        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(CircleShape).background(C.Muted.copy(alpha = 0.16f)))
        Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(CircleShape).background(C.Muted.copy(alpha = 0.16f)))
        if (status.isNotBlank()) Text(status, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- the two buttons

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.RoundButton(
    icon: ImageVector,
    label: String,
    visible: Boolean,
    align: Alignment,
    primary: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible,
        Modifier.align(align).navigationBarsPadding().padding(24.dp),
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .shadow(14.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
                .clip(CircleShape)
                .background(if (primary) C.Accent else C.Card)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) "Speak" else null),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (primary) C.OnAccent else C.Text, modifier = Modifier.size(24.dp))
        }
    }
}

/** A light top bar: a greeting for the time of day, today's date and a small accent dot. */
@Composable
private fun HeaderBar(modifier: Modifier) {
    val now = java.time.LocalDateTime.now()
    val greeting = when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hello" }
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, color = C.Text, style = MaterialTheme.typography.headlineSmall)
            Text(now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(C.Accent))
    }
}

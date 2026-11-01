package com.aicanvas.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import org.json.JSONObject
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicanvas.app.AppViewModel
import kotlinx.coroutines.launch

// The chat box at the bottom of the screen and its suggestions.

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ChatBox(vm: AppViewModel, listen: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canSend = (vm.draft.isNotBlank() || vm.photos.isNotEmpty()) && !vm.busy
    // With nothing typed, the round button is a microphone.
    val mic = vm.draft.isBlank() && vm.photos.isEmpty() && !vm.busy
    // Up to five photos in one go (the system picker needs at least 2 as its limit).
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(com.aicanvas.app.data.Limits.MAX_PHOTOS)
    ) { uris -> vm.attachPhotos(uris) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.attachPhotos(listOf(uri)) }
    val pickPhoto = {
        val left = com.aicanvas.app.data.Limits.MAX_PHOTOS - vm.photos.size
        when {
            left <= 0 -> vm.tooManyPhotos()
            left == 1 -> pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else -> pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    androidx.compose.runtime.LaunchedEffect(vm.wantsPhoto) {
        if (vm.wantsPhoto) {
            vm.wantsPhoto = false
            pickPhoto()
        }
    }
    // A copied image (a screenshot, a picture copied from the browser) can be pasted in with one tap.
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.runtime.remember { context.getSystemService(android.content.ClipboardManager::class.java) }
    var clipHasImage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ClipImages.has(clipboard)) }
    androidx.compose.runtime.DisposableEffect(clipboard) {
        val l = android.content.ClipboardManager.OnPrimaryClipChangedListener { clipHasImage = ClipImages.has(clipboard) }
        clipboard?.addPrimaryClipChangedListener(l)
        onDispose { clipboard?.removePrimaryClipChangedListener(l) }
    }
    // After sending, put the keyboard away so the new element is in view. Focus stays in the text box on purpose:
    // releasing it hands focus to the next button on screen (a +, a tick), and the rest of the Enter key press
    // would then "click" it.
    val keyboard = LocalSoftwareKeyboardController.current
    val send = {
        vm.send()
        keyboard?.hide()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(if (C.dark) C.Card else C.Bg)
            // swallow taps so they don't reach the close-on-tap layer behind
            .onTap {}
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A thin moving bar while the AI works, so it never looks frozen.
        AnimatedVisibility(vm.busy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(3.dp).clip(CircleShape),
                color = C.Accent,
                trackColor = C.Line.copy(alpha = 0.5f),
            )
        }
        val line = if (vm.busy) vm.status.ifBlank { "Thinking…" } + (if (vm.queued > 0) " · ${vm.queued} waiting" else "") else vm.reply
        AnimatedContent(line, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "reply") { text ->
            if (text != null) {
                Text(
                    text,
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    color = when { vm.busy -> C.Muted; vm.replyIsError -> C.Bad; else -> C.Text },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        // A few ideas to tap when the box is empty; each sends straight away.
        if (vm.draft.isEmpty() && vm.photos.isEmpty() && !vm.busy) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestionsFor(vm).forEach { idea ->
                    Row(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(Tones.tint(idea.tone).let { if (it == C.Card) C.Raised else it })
                            .clickable(role = Role.Button, onClickLabel = "Send") { vm.useStarter(idea) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(idea.emoji, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(idea.title, color = C.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (vm.photos.isNotEmpty() || clipHasImage) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                vm.photos.forEachIndexed { i, p ->
                    Box {
                        Image(
                            p.preview.asImageBitmap(), "Attached photo ${i + 1}",
                            Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color(0xAA000000))
                                .clickable { vm.removePhoto(i) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Remove photo ${i + 1}", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
                if (clipHasImage && vm.photos.size < com.aicanvas.app.data.Limits.MAX_PHOTOS) {
                    Row(
                        Modifier.heightIn(min = 40.dp).clip(CircleShape).background(if (C.dark) C.Raised else C.Card)
                            .clickable(role = Role.Button) { vm.attachPhotos(ClipImages.uris(clipboard)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ContentPaste, null, tint = C.Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste image", color = C.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { pickPhoto() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Image, "Add photos", tint = C.Muted, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(4.dp))
            // A TextFieldState field: keyboards (Gboard's clipboard, stickers) and long-press Paste can hand it images.
            val field = androidx.compose.foundation.text.input.rememberTextFieldState(vm.draft)
            LaunchedEffect(vm.draft) { if (field.text.toString() != vm.draft) field.setTextAndPlaceCursorAtEnd(vm.draft) }
            LaunchedEffect(field) {
                androidx.compose.runtime.snapshotFlow { field.text.toString() }.collect { t ->
                    // Enter sends instead of starting a new line.
                    if (t.endsWith('\n')) { vm.draft = t.trimEnd('\n'); field.setTextAndPlaceCursorAtEnd(vm.draft); send() } else vm.draft = t
                }
            }
            BasicTextField(
                field,
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    // Images from the keyboard or a paste become attached photos (up to the limit); text goes in as usual.
                    .contentReceiver { t ->
                        val uris = ClipImages.uris(t.clipEntry.clipData)
                        if (uris.isEmpty()) t else { vm.attachPhotos(uris); t.consume { it.uri != null } }
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (C.dark) C.Raised else C.Card)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.Text),
                cursorBrush = SolidColor(C.Text),
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                onKeyboardAction = { send() },
                decorator = { inner ->
                    Box {
                        if (field.text.isEmpty()) Text(vm.chatHint, color = C.Muted, style = MaterialTheme.typography.bodyLarge)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(10.dp))
            val sendBg by animateColorAsState(if (canSend) C.Accent else if (C.dark) C.Raised else C.Card, label = "send")
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(sendBg)
                    .clickable(enabled = canSend || mic, role = Role.Button) { if (mic) listen() else send() },
                contentAlignment = Alignment.Center,
            ) {
                if (mic) Icon(Icons.Rounded.Mic, "Speak", tint = C.Text, modifier = Modifier.size(22.dp))
                else Icon(Icons.Rounded.ArrowUpward, "Send", tint = if (canSend) C.OnAccent else C.Muted, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** True when a card has to-dos and every one of them is ticked. */
internal fun allTicked(ui: JSONObject): Boolean {
    var any = false
    var open = false
    fun walk(n: JSONObject) {
        if (n.optString("type") == "check") {
            any = true
            if (!n.optBoolean("checked")) open = true
        }
        val c = n.optJSONArray("children") ?: return
        for (i in 0 until c.length()) c.optJSONObject(i)?.let(::walk)
    }
    walk(ui)
    return any && !open
}

/** Ideas for the chat, leaving out what is already on screen. */
internal fun suggestionsFor(vm: AppViewModel): List<com.aicanvas.app.data.Starter> =
    com.aicanvas.app.data.Starters.suggest(vm.elements.joinToString(" ") { it.ui.toString() }, java.time.LocalTime.now().hour)

/** Shared look for the bottom panels. */
@Composable
fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(C.Card)
            .onTap {}
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(C.Muted.copy(alpha = 0.35f)))
        }
        content()
        Spacer(Modifier.height(8.dp))
    }
}

/** Reads image links from the clipboard or from what the keyboard sends. */
internal object ClipImages {
    fun has(cm: android.content.ClipboardManager?): Boolean =
        runCatching { cm?.primaryClipDescription?.hasMimeType("image/*") == true }.getOrDefault(false)

    fun uris(cm: android.content.ClipboardManager?): List<android.net.Uri> =
        runCatching { cm?.primaryClip?.let { uris(it) } }.getOrNull().orEmpty()

    fun uris(clip: android.content.ClipData): List<android.net.Uri> =
        (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
}

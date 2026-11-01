package com.aicanvas.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.data.CanvasInfo
import com.aicanvas.app.data.Canvases
import com.aicanvas.app.data.Element
import com.aicanvas.app.data.Refresh

/**
 * Canvas tabs on the left (tap to switch, long-press for options); on the right, apart from the tabs, a refresh icon
 * and a "⋮" canvas menu with New, Rename, Merge, Merge all into one, Delete, Organize and Refresh.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<CanvasInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CanvasInfo?>(null) }
    var merging by remember { mutableStateOf(false) }
    var mergingAll by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val current = vm.canvases.firstOrNull { it.id == vm.currentCanvas } ?: vm.canvases.first()

    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Tabs: one segmented track, the selected canvas raised and bold.
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(C.Raised).horizontalScroll(rememberScrollState()).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            vm.canvases.forEach { c -> Tab(c.name, c.id == vm.currentCanvas, onLong = { editing = c }) { vm.switchCanvas(c.id) } }
            Tab("+", false) { creating = true }
        }
        Spacer(Modifier.width(8.dp))
        IconButton({ if (!vm.refreshing) vm.refreshLive(force = true) }, enabled = !vm.refreshing) {
            Icon(Icons.Rounded.Refresh, if (vm.refreshing) "Refreshing" else "Refresh live cards", tint = if (vm.refreshing) C.Muted else C.Text)
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Canvas options", tint = C.Text) }
            DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(16.dp), containerColor = if (C.dark) C.Raised else C.Card) {
                MenuItem("New canvas") { menu = false; creating = true }
                MenuItem("Rename “${current.name}”") { menu = false; editing = current }
                MenuItem("Merge canvases…", enabled = vm.canvases.size > 1) { menu = false; merging = true }
                MenuItem("Merge all into one canvas", enabled = vm.canvases.size > 1) { menu = false; mergingAll = true }
                MenuItem("Delete “${current.name}”", enabled = current.id != Canvases.HOME_ID, danger = true) { menu = false; deleting = current }
                HorizontalDivider(color = C.Line)
                MenuItem(if (vm.organizing) "Organizing…" else "Auto-organize by category", enabled = !vm.organizing) { menu = false; vm.planOrganize() }
                MenuItem(if (vm.refreshing) "Refreshing…" else "Refresh live cards", enabled = !vm.refreshing) { menu = false; vm.refreshLive(force = true) }
            }
        }
    }

    if (creating) NameDialog("New canvas", "") { name -> creating = false; name?.let { vm.createCanvas(it) } }
    editing?.let { c ->
        NameDialog("Rename canvas", c.name, onDelete = if (c.id == Canvases.HOME_ID) null else ({ editing = null; deleting = c })) { name ->
            editing = null; name?.let { vm.renameCanvas(c.id, it) }
        }
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${c.name}”?") },
            text = { Text("Its cards move to Home. You can undo this.") },
            confirmButton = { TextButton({ deleting = null; vm.deleteCanvas(c.id) }) { Text("Delete", color = C.Bad) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
    if (mergingAll) AlertDialog(
        onDismissRequest = { mergingAll = false },
        title = { Text("Merge all into one canvas?") },
        text = { Text("All ${vm.elements.size} cards move to Home and the other ${vm.canvases.size - 1} canvases are removed. You can undo this.") },
        confirmButton = { TextButton({ mergingAll = false; vm.mergeAllCanvases() }) { Text("Merge all") } },
        dismissButton = { TextButton({ mergingAll = false }) { Text("Cancel") } },
    )
    if (merging) MergeDialog(vm) { merging = false }
}

/** Pick two or more canvases and a name; cards move onto one, the others are removed. */
@Composable
private fun MergeDialog(vm: AppViewModel, onClose: () -> Unit) {
    val picked = remember { androidx.compose.runtime.mutableStateListOf(vm.currentCanvas) }
    var name by remember { mutableStateOf<String?>(null) }
    val defaultName = (vm.canvases.firstOrNull { it.id == Canvases.HOME_ID && it.id in picked } ?: vm.canvases.firstOrNull { it.id in picked })?.name.orEmpty()
    var confirm by remember { mutableStateOf(false) }
    val finalName = (name ?: defaultName).trim()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (confirm) "Merge ${picked.size} canvases?" else "Merge canvases") },
        text = {
            if (confirm) {
                val n = vm.elements.count { it.canvas in picked }
                Text("$n cards move into “$finalName”; the other ${picked.size - 1} canvases are removed. You can undo this.")
            } else Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                vm.canvases.forEach { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.id in picked, { on -> if (on) picked += c.id else picked -= c.id })
                        Text(c.name + "  ·  ${vm.elements.count { it.canvas == c.id }} cards", color = C.Text)
                    }
                }
                OutlinedTextField(name ?: defaultName, { name = it }, singleLine = true, label = { Text("Merged canvas name") }, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            if (confirm) TextButton({ onClose(); vm.mergeCanvases(picked.toList(), finalName) }) { Text("Merge") }
            else TextButton({ confirm = true }, enabled = picked.size >= 2 && finalName.isNotEmpty()) { Text("Next") }
        },
        dismissButton = { TextButton(onClose) { Text("Cancel") } },
    )
}

/** Dialogs driven by the view model, kept at the screen root so scrolling never hides them. */
@Composable
fun CanvasDialogs(vm: AppViewModel) {
    vm.organizePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { vm.cancelOrganize() },
            title = { Text("Organize cards") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (plan.isEmpty()) Text("Everything is already in place.")
                    plan.groupBy { it.category }.forEach { (cat, moves) ->
                        Text(cat, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        moves.forEach { Text("• ${it.title}", color = C.Muted, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            },
            confirmButton = { TextButton({ vm.applyOrganize() }, enabled = plan.isNotEmpty()) { Text("Move ${plan.size}") } },
            dismissButton = { TextButton({ vm.cancelOrganize() }) { Text("Cancel") } },
        )
    }
    vm.pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text(if (p.ids.size == 1) "Delete this card?" else "Delete ${p.ids.size} cards?") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text("The AI wants to delete:", color = C.Muted)
                    p.titles.forEach { Text("• $it") }
                    Text("You can undo this right after.", color = C.Muted, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton({ vm.confirmDelete() }) { Text("Delete", color = C.Bad) } },
            dismissButton = { TextButton({ vm.cancelDelete() }) { Text("Keep") } },
        )
    }
}

@Composable
private fun MenuItem(label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(label, color = if (!enabled) C.Muted else if (danger) C.Bad else C.Text) }, onClick = onClick, enabled = enabled)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tab(label: String, selected: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Text(
        label,
        color = if (selected) C.Text else C.Muted,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(shape)
            .then(if (selected) Modifier.background(C.Card).border(1.dp, C.Line, shape) else Modifier)
            .combinedClickable(onLongClick = onLong, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDelete: (() -> Unit)? = null, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton({ onDone(text.trim().takeIf { it.isNotEmpty() }) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onDelete) { Text("Delete", color = C.Bad) }
                Spacer(Modifier.width(4.dp))
                TextButton({ onDone(null) }) { Text("Cancel") }
            }
        },
    )
}

/** "Updated 3 h ago" under a live card, with a tap to refresh now. */
@Composable
fun LiveFooter(e: Element, vm: AppViewModel) {
    if (vm.specFor(e) == null) return
    val now = System.currentTimeMillis()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (e.refreshedAt > 0) Refresh.ago(e.refreshedAt, now) else "Live card", color = C.Muted,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        TextButton({ vm.refreshLive(force = true) }, enabled = !vm.refreshing) {
            Text(if (vm.refreshing) "Refreshing…" else "Refresh", style = MaterialTheme.typography.labelSmall)
        }
    }
}

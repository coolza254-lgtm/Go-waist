package com.gowaist.app.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gowaist.app.R
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.IconBlob
import com.gowaist.app.ui.components.color
import com.gowaist.app.ui.components.icon
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.model.Equipment
import com.gowaist.core.model.MovementPattern

fun ExerciseEntity.matches(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || nameTh.contains(q, true) || nameEn.contains(q, true)
}

/** Full-screen exercise chooser. With [multi] the user can tick several and confirm. */
@Composable
fun ExercisePickerDialog(
    exercises: List<ExerciseEntity>,
    owned: Set<Equipment>,
    multi: Boolean,
    onDismiss: () -> Unit,
    onPicked: (List<ExerciseEntity>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf<MovementPattern?>(null) }
    var picked by remember { mutableStateOf(listOf<ExerciseEntity>()) }
    val list = exercises
        .filter { !it.isHidden && it.matches(query) && (pattern == null || it.pattern == pattern) }
        .sortedWith(compareByDescending<ExerciseEntity> { it.isFavorite }.thenBy { !it.equipment.all { e -> e == Equipment.NONE || e in owned } }.thenBy { it.difficulty })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                Text(stringResource(R.string.pick_exercise), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                query, { query = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(R.string.lib_search)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(pattern == null, { pattern = null }, label = { Text(stringResource(R.string.lib_all)) }) }
                items(MovementPattern.entries) { p -> FilterChip(pattern == p, { pattern = if (pattern == p) null else p }, label = { Text(stringResource(p.labelRes())) }) }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { ex ->
                    val selected = ex in picked
                    val doable = ex.equipment.all { it == Equipment.NONE || it in owned }
                    Row(
                        Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else Gw.colors.card, MaterialTheme.shapes.medium)
                            .clickable {
                                if (multi) picked = if (selected) picked - ex else picked + ex else onPicked(listOf(ex))
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBlob(ex.pattern.icon(), ex.pattern.color().copy(alpha = if (doable) 1f else 0.4f), size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.nameTh, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ex.nameEn + " · " + "★".repeat(ex.difficulty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (ex.isFavorite) Icon(Icons.Rounded.Star, null, tint = Gw.colors.gold, modifier = Modifier.size(20.dp))
                        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (multi) {
                Box(Modifier.padding(16.dp)) {
                    GameButton(stringResource(R.string.pick_done, picked.size), { onPicked(picked) }, enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

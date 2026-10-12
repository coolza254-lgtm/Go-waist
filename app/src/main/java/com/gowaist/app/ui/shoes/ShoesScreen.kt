package com.gowaist.app.ui.shoes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.repo.ShoeRepository
import com.gowaist.app.data.repo.ShoeWithStats
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.HpBar
import com.gowaist.app.ui.components.LocalImage
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.RoundGameButton
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.ShoeIcon
import com.gowaist.app.ui.components.StatBlock
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.components.panel
import com.gowaist.app.ui.nav.ShoeEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ShoesViewModel @Inject constructor(repo: ShoeRepository) : ViewModel() {
    val shoes = repo.shoes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

fun baht(v: Double, digits: Int = 0): String = "฿" + Format.decimal(v, digits).let { s ->
    // Thousands separators for prices.
    val parts = s.split('.')
    parts[0].reversed().chunked(3).joinToString(",").reversed() + (parts.getOrNull(1)?.let { ".$it" } ?: "")
}

@Composable
fun ShoesScreen(nav: NavHostController, vm: ShoesViewModel = hiltViewModel()) {
    val shoes by vm.shoes.collectAsStateWithLifecycle()
    val list = shoes ?: return
    val active = list.filter { !it.shoe.retired }
    val retired = list.filter { it.shoe.retired }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_shoes), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            RoundGameButton(Icons.Rounded.Add, stringResource(R.string.shoe_add), { nav.navigate(ShoeEditRoute()) }, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("shoe_add"))
        }
        if (list.isEmpty()) {
            EmptyState(
                stringResource(R.string.shoe_empty_title), stringResource(R.string.shoe_empty_text), icon = ShoeIcon,
                actionText = stringResource(R.string.shoe_add), onAction = { nav.navigate(ShoeEditRoute()) },
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val totalM = list.sumOf { it.stats.distanceM }
            val priced = list.filter { it.shoe.priceBaht != null && it.stats.distanceM >= 1000 }
            val avgCost = priced.sumOf { it.shoe.priceBaht!! }.takeIf { priced.isNotEmpty() }?.let { it / (priced.sumOf { s -> s.stats.distanceM } / 1000) }
            StatTile(stringResource(R.string.shoe_total_km), Fmt.distanceValue(totalM, 0), Modifier.weight(1f))
            StatTile(stringResource(R.string.shoe_avg_cost), avgCost?.let { baht(it, 1) } ?: "-", Modifier.weight(1f))
            StatTile(stringResource(R.string.shoe_active), "${active.size}", Modifier.weight(1f))
        }
        active.forEach { s ->
            if (s.shoe.isMain) MainShoeCard(s) { nav.navigate(ShoeEditRoute(s.shoe.id)) } else ShoeRow(s) { nav.navigate(ShoeEditRoute(s.shoe.id)) }
        }
        if (retired.isNotEmpty()) {
            SectionHeader(stringResource(R.string.shoe_retired_title))
            retired.forEach { s -> ShoeRow(s, retired = true) { nav.navigate(ShoeEditRoute(s.shoe.id)) } }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ShoePhoto(path: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).panel(MaterialTheme.colorScheme.background, Gw.colors.ink, radius = 8.dp, shadow = 0.dp, border = 1.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            LocalImage(path, stringResource(R.string.shoe_photo), Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)), maxSize = 480, contentScale = ContentScale.Crop)
        } else {
            Icon(ShoeIcon, stringResource(R.string.shoe_photo), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.6f))
        }
    }
}

@Composable
private fun MainShoeCard(s: ShoeWithStats, onClick: () -> Unit) {
    GameCard(Modifier.fillMaxWidth().testTag("shoe_main"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShoePhoto(s.shoe.imagePath, 84.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Pill(stringResource(R.string.shoe_main), Gw.colors.goal, icon = Icons.Rounded.Star, filled = true)
                Text(s.shoe.name, style = MaterialTheme.typography.titleLarge)
                Text(purchaseLine(s), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(stringResource(R.string.shoe_km_used), Fmt.distanceValue(s.stats.distanceM, 0), Modifier.weight(1f))
            StatBlock(stringResource(R.string.shoe_cost_per_km), s.stats.costPerKm?.let { baht(it, 1) } ?: "-", Modifier.weight(1f))
            StatBlock(stringResource(R.string.shoe_runs), "${s.stats.runs}", Modifier.weight(1f))
        }
        Durability(s)
    }
}

@Composable
private fun Durability(s: ShoeWithStats) {
    val pct = s.shoe.durabilityPct ?: return
    Spacer(Modifier.height(12.dp))
    Row {
        Text(stringResource(R.string.shoe_durability), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text("$pct%", style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(4.dp))
    HpBar(pct)
    s.kmLeft?.let {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.shoe_km_left, Fmt.distance(it * 1000, 0)), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
    }
}

@Composable
private fun ShoeRow(s: ShoeWithStats, retired: Boolean = false, onClick: () -> Unit) {
    GameCard(Modifier.fillMaxWidth().alpha(if (retired) 0.7f else 1f), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShoePhoto(s.shoe.imagePath, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.shoe.name + if (retired) " · " + stringResource(R.string.shoe_retired) else "",
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (retired) TextDecoration.LineThrough else null,
                )
                Text(
                    Fmt.distance(s.stats.distanceM, 0) + (s.stats.costPerKm?.let { " · " + baht(it, 1) + stringResource(R.string.shoe_per_km_suffix) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted,
                )
            }
            s.shoe.durabilityPct?.let { Text("$it%", style = MaterialTheme.typography.titleMedium) }
        }
        if (!retired) s.shoe.durabilityPct?.let {
            Spacer(Modifier.height(8.dp))
            HpBar(it, height = 10.dp)
        }
    }
}

@Composable
private fun purchaseLine(s: ShoeWithStats): String = listOfNotNull(
    s.shoe.purchaseDate?.let { stringResource(R.string.shoe_bought, Fmt.dateMedium(it.toLocalDate())) },
    s.shoe.priceBaht?.let { baht(it) },
).joinToString(" · ").ifEmpty { " " }

package com.sxdpz23.navisarthi.navigation.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.R
import com.sxdpz23.navisarthi.navigation.ui.QuickCategories
import com.sxdpz23.navisarthi.navigation.ui.NavigationMenu
import com.sxdpz23.navisarthi.navigation.ui.dpadHighlight
import com.sxdpz23.navisarthi.navigation.ui.icons.Sym
import com.sxdpz23.navisarthi.navigation.ui.item

/**
 * The last chip on the map's category row (issue #779): a menu of every chip in the user's
 * order, the ones switched off included, so a hidden category is still one tap away. A pick
 * runs that search like the chip would.
 */
@Composable
internal fun MoreCategoriesChip(onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        NavGlassChip(stringResource(R.string.cat_more), { open = true }, Modifier.dpadHighlight(CircleShape)) {
            Icon(Sym.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        NavigationMenu(expanded = open, onDismissRequest = { open = false }, glass = true) {
            QuickCategories.arranged().forEach { chip ->
                item(stringResource(chip.label), chip.icon) { open = false; onPick(chip.query) }
            }
        }
    }
}

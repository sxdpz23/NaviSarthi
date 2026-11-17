package com.sxdpz23.navisarthi.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.ui.theme.Gold

/** Focus is a state, not a decorative glow. Clickable supplies the focus target. */
@Composable
internal fun Modifier.craftFocus(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(2.dp, Gold, shape) else Modifier)
}

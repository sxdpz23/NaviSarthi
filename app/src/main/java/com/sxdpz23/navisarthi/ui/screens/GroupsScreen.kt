package com.sxdpz23.navisarthi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sxdpz23.navisarthi.ui.NaviSarthiIcons
import com.sxdpz23.navisarthi.ui.components.ScreenHeader
import com.sxdpz23.navisarthi.ui.theme.*

@Composable
fun GroupsScreen() {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp).padding(bottom = 112.dp)) {
        ScreenHeader(title = "Groups")
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Icon(NaviSarthiIcons.HelmetIntercom, null, tint = TextMid, modifier = Modifier.size(48.dp))
            Text("Feature coming soon", color = TextHi, fontFamily = GeistFamily, fontSize = 22.sp)
        }
    }
}

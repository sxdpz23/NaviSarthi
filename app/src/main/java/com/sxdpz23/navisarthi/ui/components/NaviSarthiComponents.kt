package com.sxdpz23.navisarthi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sxdpz23.navisarthi.ui.theme.*

// ---- Shape constants ----
val CardShape    = RoundedCornerShape(24.dp)
val BtnShape     = RoundedCornerShape(16.dp)
val InputShape   = RoundedCornerShape(16.dp)
val ChipShape    = CircleShape
val IconBtnShape = RoundedCornerShape(16.dp)

object ControlSpacing {
    val Item = 8.dp
    val PageHorizontal = 20.dp
    val PageTop = 20.dp
    val PageBottom = 120.dp
}

// ---- Card ----
@Composable
fun NaviSarthiCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    padding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = if (glow)
        Modifier.border(1.dp, GoldTint2, CardShape)
    else
        Modifier.border(1.dp, Line, CardShape)

    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Surf1)
            .then(border)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

// ---- Buttons ----
enum class BtnVariant { Primary, Secondary, Ghost, Danger }
enum class BtnSize { Lg, Md, Sm }

@Composable
fun NaviSarthiBtn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: BtnVariant = BtnVariant.Primary,
    size: BtnSize = BtnSize.Md,
    enabled: Boolean = true,
) {
    val (background, foreground, border) = when (variant) {
        BtnVariant.Primary -> Triple(Gold, OnGold, Color.Transparent)
        BtnVariant.Secondary -> Triple(Surf2, TextHi, Line2)
        BtnVariant.Ghost -> Triple(Color.Transparent, TextMid, Line)
        BtnVariant.Danger -> Triple(Color.Transparent, Alert, Alert.copy(alpha = .24f))
    }
    val height = if (size == BtnSize.Lg) 56.dp else 48.dp
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.heightIn(min = height)
            .craftFocus(BtnShape).clip(BtnShape)
            .background(if (enabled) background else Surf2)
            .border(1.dp, if (enabled) border else Line, BtnShape)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) foreground else TextDis, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = if (enabled) foreground else TextDis,
            fontSize = if (size == BtnSize.Sm) 14.sp else 16.sp,
            fontWeight = FontWeight.Medium, fontFamily = GeistFamily,
            letterSpacing = 0.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---- Icon button (square rounded) ----
@Composable
fun NaviSarthiIconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    active: Boolean = false,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val bg = if (active) GoldTint else Surf2
    val bd = if (active) GoldTint2 else Line2
    val iconTint = tint ?: if (active) Gold else TextMid

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(size)
            .craftFocus(IconBtnShape).clip(IconBtnShape)
            .background(bg)
            .border(1.dp, bd, IconBtnShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

// ---- Chip ----
enum class ChipTone { Gold, Warn, Alert, Off, Neutral }

@Composable
fun NaviSarthiChip(
    label: String,
    tone: ChipTone = ChipTone.Neutral,
    dot: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val (bg, fg, bd) = when (tone) {
        ChipTone.Gold    -> Triple(GoldTint, Gold, GoldTint2)
        ChipTone.Warn    -> Triple(Warn.copy(alpha = 0.13f), Warn, Warn.copy(alpha = 0.3f))
        ChipTone.Alert   -> Triple(Alert.copy(alpha = 0.13f), Alert, Alert.copy(alpha = 0.32f))
        ChipTone.Off     -> Triple(Surf1, TextLo, Line)
        ChipTone.Neutral -> Triple(Surf2, TextMid, Line2)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .height(30.dp)
            .clip(ChipShape)
            .background(bg)
            .border(1.dp, bd, ChipShape)
            .padding(horizontal = 12.dp),
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        }
        Text(
            label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = GeistFamily, maxLines = 1,
        )
    }
}

// ---- Toggle ----
@Composable
fun NaviSarthiToggle(on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    VehicleGlassSwitch(on, onChange, enabled = enabled)

}

// ---- Segmented control ----
@Composable
fun NaviSarthiSegmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .selectableGroup().clip(RoundedCornerShape(12.dp))
            .background(Surf1)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { opt ->
            val active = opt == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .craftFocus(RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                    .background(if (active) Surf3 else Color.Transparent)
                    .selectable(selected = active, role = androidx.compose.ui.semantics.Role.Tab, onClick = { onSelect(opt) }),
            ) {
                Text(
                    opt, color = if (active) TextHi else TextLo,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    fontFamily = GeistFamily,
                )
            }
        }
    }
}

// ---- Divider ----
@Composable
fun NaviSarthiDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Line))
}

// ---- Eyebrow label ----
/** Consistent hierarchy for groups of controls on a scrolling screen. */
@Composable
fun ControlSection(title: String, modifier: Modifier = Modifier) {
    Eyebrow(title, modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
fun ControlHint(text: String, modifier: Modifier = Modifier, topPadding: Dp = 8.dp) {
    Text(
        text = text,
        modifier = modifier.padding(top = topPadding),
        color = TextLo,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}

/** The parent supplies the 8 dp gap, keeping help text visually attached to its option. */
@Composable
fun SpacedControlHint(text: String, modifier: Modifier = Modifier) {
    ControlHint(text, modifier, topPadding = 0.dp)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextLo,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

// ---- List row ----
enum class IconTone { Neutral, Gold, Warn, Alert }

@Composable
fun NaviSarthiRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTone: IconTone = IconTone.Neutral,
    sub: String? = null,
    right: String? = null,
    rightSub: String? = null,
    accentRight: Boolean = false,
    trailingIcon: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val (iconBg, iconFg) = when (iconTone) {
        IconTone.Gold   -> GoldTint to Gold
        IconTone.Alert  -> Alert.copy(alpha = 0.13f) to Alert
        IconTone.Warn   -> Warn.copy(alpha = 0.13f) to Warn
        IconTone.Neutral -> Surf2 to TextMid
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .craftFocus(CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg)
                    .border(1.dp, Line, RoundedCornerShape(12.dp)),
            ) {
                Icon(icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                fontFamily = GeistFamily, letterSpacing = 0.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(sub, color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (right != null || rightSub != null) {
            Column(horizontalAlignment = Alignment.End) {
                if (right != null) {
                    Text(
                        right,
                        color = if (accentRight) Gold else TextHi,
                        fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        fontFamily = GeistMonoFamily,
                    )
                }
                if (rightSub != null) {
                    Text(rightSub, color = TextLo, fontSize = 12.sp)
                }
            }
        }
        if (trailingIcon || (onClick != null && right == null)) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null, tint = TextLo, modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---- Screen scaffold (scrollable body) ----
@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    title: String? = null,
    eyebrow: String? = null,
    hint: String? = null,
    wordmark: Boolean = false,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .defaultMinSize(minHeight = 48.dp),
    ) {
        if (onBack != null) {
            NaviSarthiIconBtn(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                onClick = onBack,
                contentDescription = androidx.compose.ui.res.stringResource(com.sxdpz23.navisarthi.R.string.craft_back),
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            if (wordmark) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.sxdpz23.navisarthi.R.drawable.navisarthi_logo),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "NaviSarthi",
                        color = TextHi, fontFamily = GeistMonoFamily,
                        fontWeight = FontWeight.Medium, fontSize = 20.sp,
                        letterSpacing = 0.14.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
            } else {
                Column {
                    if (title != null) {
                        Text(
                            title, color = TextHi, fontSize = 24.sp,
                            fontWeight = FontWeight.Medium, fontFamily = GeistFamily,
                            letterSpacing = 0.sp, lineHeight = 32.sp,
                        )
                    }
                    if (eyebrow != null) Eyebrow(eyebrow, Modifier.padding(top = 4.dp))
                    // One-line "what this screen is for" guidance, shown right under the title.
                    if (hint != null) {
                        Text(
                            hint, color = TextLo, fontSize = 14.sp, fontFamily = GeistFamily,
                            lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                        )
                    }
                }
            }
        }
        trailing?.invoke()
    }
}

package md.vicproj.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Corner = RoundedCornerShape(4.dp)

/** Buton cu contur alb; la atingere se umple cu alb (ca "hover" din Windows). */
@Composable
fun MonoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = if (enabled && pressed) VicColors.Foreground else VicColors.Background
    val fg = when {
        !enabled -> VicColors.MutedDim
        pressed -> VicColors.Background
        else -> VicColors.Foreground
    }
    val borderColor = if (enabled) VicColors.Foreground else VicColors.Border
    Box(
        modifier = modifier
            .height(height)
            .clip(Corner)
            .background(bg)
            .border(1.dp, borderColor, Corner)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Buton principal: alb plin, text negru. Dezactivat: gri inchis. */
@Composable
fun MonoButtonPrimary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when {
        !enabled -> VicColors.Border
        pressed -> VicColors.Press
        else -> VicColors.Foreground
    }
    val fg = if (enabled) VicColors.Background else VicColors.MutedDim
    Box(
        modifier = modifier
            .height(height)
            .clip(Corner)
            .background(bg)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text = text,
        color = VicColors.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Camp de text: fundal Surface, bordura gri (alba cand are focus), ca UiFactory.WrapInput din Windows. */
@Composable
fun MonoInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    mono: Boolean = false,
    big: Boolean = false,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    singleLine: Boolean = true,
    minHeight: Dp = 50.dp,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused && enabled) VicColors.Foreground else VicColors.MutedDim
    val textColor = if (enabled) VicColors.Foreground else VicColors.MutedDim
    val fontSize = if (big) 24.sp else if (mono) 18.sp else 15.sp

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .background(VicColors.Surface)
            .border(1.dp, borderColor),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = textColor,
                fontSize = fontSize,
                fontFamily = if (mono || big) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
            ),
            cursorBrush = SolidColor(VicColors.Foreground),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onAction() },
                onGo = { onAction() },
                onSearch = { onAction() },
                onSend = { onAction() },
            ),
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = VicColors.MutedDim, fontSize = fontSize)
                    }
                    inner()
                }
            },
        )
    }
}

/** Bara de progres subtire (6 dp), alb pe fundal SurfaceAlt, ca MonoProgressBar. */
@Composable
fun MonoProgress(progress: Int?, modifier: Modifier = Modifier) {
    val fraction = ((progress ?: 0).coerceIn(0, 100) / 100f).coerceAtLeast(0.02f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(VicColors.SurfaceAlt),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(VicColors.Foreground),
        )
    }
}

/** Bara de jos: punct + stare conexiune (stanga) si mesaje scurte (dreapta). */
@Composable
fun StatusBar(state: ConnectivityState, flash: String) {
    val label: String
    val on: Boolean
    when (state) {
        ConnectivityState.Online -> { label = "CONECTAT"; on = true }
        ConnectivityState.Unknown -> { label = "Se verifică…"; on = false }
        ConnectivityState.ServerUnreachable -> { label = "Server indisponibil"; on = false }
        ConnectivityState.Offline -> { label = "Fără conexiune"; on = false }
    }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(VicColors.Border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VicColors.Surface)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (on) VicColors.Foreground else VicColors.MutedDim),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, color = VicColors.Muted, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.width(12.dp))
            Text(
                text = flash,
                color = VicColors.Foreground,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Ecran derulabil, cu continutul centrat pe verticala cand incape (si derulabil cand nu). */
@Composable
fun CenteredScroll(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        val minH = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minH)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

fun copyText(context: Context, label: String, text: String): Boolean {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    } catch (e: Exception) {
        false
    }
}

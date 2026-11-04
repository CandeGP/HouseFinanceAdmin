package mx.finanzas.pareja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.ui.AppTheme

private val ButtonShape = RoundedCornerShape(18.dp)
private val FieldShape = RoundedCornerShape(16.dp)

/** La casa del logo, dibujada con trazo fino sobre una cuadrícula de 24 (igual que en los mockups). */
@Composable
private fun HouseGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(vararg points: Pair<Float, Float>) {
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * unit, y * unit) else path.lineTo(x * unit, y * unit) }
            drawPath(path, color, style = stroke)
        }
        line(3f to 10.5f, 12f to 4f, 21f to 10.5f)
        line(5f to 9.5f, 5f to 19f, 19f to 19f, 19f to 9.5f)
        line(9.5f to 19f, 9.5f to 14f, 14.5f to 14f, 14.5f to 19f)
    }
}

/** Logo de la app: la casa sobre dos tarjetas inclinadas. Mide 240 × 150, como en el mockup del login. */
@Composable
fun AppLogoMark(modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    val cardShape = RoundedCornerShape(18.dp)
    Box(modifier.size(width = 240.dp, height = 132.dp)) {
        Box(
            Modifier.align(Alignment.TopCenter).offset(x = (-34).dp, y = 18.dp).size(width = 150.dp, height = 92.dp)
                .rotate(-12f).alpha(0.9f).clip(cardShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFF6FA8), Color(0xFFFFB547)))),
        )
        Box(
            Modifier.align(Alignment.TopCenter).offset(x = 34.dp, y = 10.dp).size(width = 150.dp, height = 92.dp)
                .rotate(9f).alpha(0.95f).clip(cardShape)
                .background(Brush.linearGradient(listOf(Color(0xFF3DD6D0), Color(0xFF4F8CFF)))),
        )
        Box(
            Modifier.align(Alignment.TopCenter).size(78.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x8C5B8CFF), spotColor = Color(0x8C5B8CFF))
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF8B7CFF), Color(0xFF5B8CFF)))),
            contentAlignment = Alignment.Center,
        ) {
            HouseGlyph(tokens.onAccent, 38.dp)
        }
    }
}

/** Botón principal del diseño: degradado de la apariencia activa con su resplandor. Se atenúa si está deshabilitado. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = AppTheme.tokens
    Row(
        modifier.height(58.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .shadow(14.dp, ButtonShape, ambientColor = tokens.accentGlow, spotColor = tokens.accentGlow)
            .clip(ButtonShape)
            .background(tokens.accent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides tokens.onAccent) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)) {
                content()
            }
        }
    }
}

/**
 * Campo de los mockups: etiqueta arriba, caja de vidrio de 56 con icono a la izquierda y, si hace falta, un
 * elemento a la derecha (p. ej. el ojo de la contraseña).
 */
@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    error: String? = null,
    enabled: Boolean = true,
) {
    val tokens = AppTheme.tokens
    val ink = MaterialTheme.colorScheme.onSurface
    val iconTint = lerp(MaterialTheme.colorScheme.primary, ink, 0.45f)
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(56.dp)
                .clip(FieldShape)
                .background(tokens.glass)
                .border(1.dp, if (error != null) MaterialTheme.colorScheme.error else tokens.glassBorder, FieldShape)
                .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leadingIcon?.let { Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink.copy(alpha = 0.45f))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
            trailing?.invoke()
        }
        if (error != null) {
            Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

/** Campo de texto con fondo de vidrio y esquinas redondeadas (formularios de primer uso). */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier = modifier,
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = tokens.glass,
            unfocusedContainerColor = tokens.glass,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = tokens.glassBorder,
        ),
    )
}

// Trazos de la «G» de Google en una cuadrícula de 24, copiados del mockup.
private const val G_BLUE = "M22.5 12.2c0-.8-.1-1.5-.2-2.2H12v4.3h5.9a5 5 0 0 1-2.2 3.3v2.7h3.5c2-1.9 3.3-4.7 3.3-8.1Z"
private const val G_GREEN = "M12 23c3 0 5.4-1 7.2-2.7l-3.5-2.7c-1 .7-2.2 1-3.7 1-2.8 0-5.2-1.9-6-4.5H2.4v2.8A11 11 0 0 0 12 23Z"
private const val G_YELLOW = "M6 14.1a6.6 6.6 0 0 1 0-4.2V7.1H2.4a11 11 0 0 0 0 9.8L6 14.1Z"
private const val G_RED = "M12 5.4c1.6 0 3 .6 4.2 1.6l3.1-3.1A11 11 0 0 0 2.4 7.1L6 9.9c.8-2.6 3.2-4.5 6-4.5Z"

@Composable
private fun GoogleGlyph(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val factor = this.size.minDimension / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            listOf(G_BLUE to 0xFF4285F4, G_GREEN to 0xFF34A853, G_YELLOW to 0xFFFBBC05, G_RED to 0xFFEA4335).forEach { (d, argb) ->
                drawPath(PathParser().parsePathString(d).toPath(), Color(argb))
            }
        }
    }
}

/**
 * Lugar reservado para «Continuar con Google». Todavía no hace nada: el inicio de sesión con Google llegará
 * después, y así el diseño ya lo trae.
 */
@Composable
fun GoogleSignInPlaceholder(modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(top = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HorizontalDivider(Modifier.weight(1f), color = tokens.glassBorder)
            Text("o continúa con", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
            HorizontalDivider(Modifier.weight(1f), color = tokens.glassBorder)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().height(56.dp).alpha(0.9f).semantics { disabled() },
            shape = ButtonShape,
            color = tokens.glass,
            border = BorderStroke(1.dp, tokens.glassBorder),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoogleGlyph(20.dp)
                    Text("Google", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Próximamente",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.warning,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(tokens.warning.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Encabezado de progreso del onboarding: puntos (el actual es alargado) y «N de M». */
@Composable
fun StepHeader(step: Int, total: Int, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (index in 1..total) {
                Box(
                    Modifier.size(width = if (index == step) 26.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (index == step) ink else ink.copy(alpha = 0.35f)),
                )
            }
        }
        Text("$step de $total", fontSize = 13.sp, color = ink.copy(alpha = 0.7f))
    }
}

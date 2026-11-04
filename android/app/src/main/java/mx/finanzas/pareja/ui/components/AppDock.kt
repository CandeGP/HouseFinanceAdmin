package mx.finanzas.pareja.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.ui.AppTheme
import kotlin.math.cos
import kotlin.math.sin

data class DockTab(val route: String, val label: String, val icon: ImageVector)

/** Opción del menú del «+» (se abre manteniéndolo presionado). */
data class AddAction(val label: String, val icon: ImageVector, val colors: List<Color>, val onSelect: () -> Unit)

private val DockHeight = 72.dp
private val FabSize = 68.dp
private val FabLift = 22.dp
private val DockSideMargin = 18.dp
private val DockBottomMargin = 12.dp
private val ArcRadius = 150.dp
private val ActionSize = 58.dp
private val HoverRadius = 46.dp
private const val ARC_START_DEG = 160.0
private const val ARC_END_DEG = 20.0

/** Ángulo (en grados, desde la derecha) de la opción [index] de [count], repartidas en arco sobre el «+». */
private fun arcAngleRadians(index: Int, count: Int): Double {
    val step = if (count > 1) (ARC_START_DEG - ARC_END_DEG) / (count - 1) else 0.0
    return Math.toRadians(ARC_START_DEG - step * index)
}

/**
 * Barra inferior flotante con el «+» al centro. Un toque en el «+» registra un cobro. Mantenerlo presionado abre
 * el menú de opciones: se puede soltar y tocar una, o deslizar el dedo sobre una y soltar. Para TalkBack las
 * opciones también cuelgan del «+» como acciones personalizadas.
 */
@Composable
fun AppDock(
    left: List<DockTab>,
    right: List<DockTab>,
    currentRoute: String?,
    onTab: (String) -> Unit,
    onAdd: () -> Unit,
    onAddLongPress: () -> Unit,
    /** Posición del dedo (en la ventana) mientras sigue abajo después de mantener el «+». */
    onAddDrag: (Offset) -> Unit,
    /** El dedo se levantó después de mantener el «+». */
    onAddRelease: () -> Unit,
    addActions: List<AddAction>,
) {
    val tokens = AppTheme.tokens
    val haptics = LocalHapticFeedback.current
    var fabOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = DockSideMargin, end = DockSideMargin, top = FabLift, bottom = DockBottomMargin),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(DockHeight),
            shape = RoundedCornerShape(30.dp),
            color = tokens.dock,
            border = BorderStroke(1.dp, tokens.dockBorder),
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                left.forEach { DockItem(it, it.route == currentRoute, onTab) }
                Spacer(Modifier.width(FabSize))
                right.forEach { DockItem(it, it.route == currentRoute, onTab) }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = -FabLift)
                .size(FabSize)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(tokens.accent)
                .border(4.dp, tokens.dock, CircleShape)
                .onGloballyPositioned { fabOrigin = it.positionInWindow() }
                .semantics {
                    contentDescription = "Agregar"
                    onClick("Nuevo gasto") { onAdd(); true }
                    onLongClick("Más opciones") { onAddLongPress(); true }
                    customActions = addActions.map { action ->
                        CustomAccessibilityAction(action.label) { action.onSelect(); true }
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // true = soltó a tiempo (toque), false = se canceló, null = se mantuvo presionado.
                        val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation() != null
                        }
                        when (tapped) {
                            true -> onAdd()
                            false -> Unit
                            null -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddLongPress()
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    onAddDrag(fabOrigin + change.position)
                                    change.consume()
                                    if (!change.pressed) break
                                }
                                onAddRelease()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = tokens.onAccent, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun DockItem(tab: DockTab, selected: Boolean, onTab: (String) -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab.route) }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(
            tab.label,
            color = color,
            fontSize = (11f / LocalDensity.current.fontScale.coerceAtLeast(1f)).sp, // la barra no crece con la letra grande: no cabría
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * Menú del «+»: las opciones saltan en arco sobre el botón. Tocar fuera o la X lo cierra. Mientras el dedo sigue
 * abajo ([dragPoint]), la opción que queda debajo se resalta y se avisa en [onHover].
 */
@Composable
fun AddMenuOverlay(
    visible: Boolean,
    actions: List<AddAction>,
    dragPoint: Offset?,
    onHover: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val density = LocalDensity.current
        var origin by remember { mutableStateOf(Offset.Zero) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val centerX = maxWidth / 2
            // Centro del «+»: sobre el borde inferior de la barra, elevado a la mitad del botón.
            val centerY = maxHeight - navBottom - DockBottomMargin - DockHeight - FabLift + FabSize / 2
            val centers: List<Pair<Dp, Dp>> = actions.indices.map { index ->
                val radians = arcAngleRadians(index, actions.size)
                (centerX + ArcRadius * cos(radians).toFloat()) to (centerY - ArcRadius * sin(radians).toFloat())
            }
            val hovered: Int? = dragPoint?.let { point ->
                val reach = with(density) { HoverRadius.toPx() }
                centers.indices
                    .map { it to (with(density) { Offset(centers[it].first.toPx(), centers[it].second.toPx()) } + origin - point).getDistance() }
                    .minByOrNull { it.second }
                    ?.takeIf { it.second <= reach }
                    ?.first
            }
            LaunchedEffect(hovered) { onHover(hovered) }

            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xCC080618))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Cerrar menú",
                        onClick = onDismiss,
                    ),
            )

            actions.forEachIndexed { index, action ->
                val (x, y) = centers[index]
                val active = hovered == index
                val scale by animateFloatAsState(if (active) 1.2f else 1f, label = "add-action-scale")
                Box(
                    Modifier.offset(x - ActionSize / 2, y - ActionSize / 2)
                        .size(ActionSize)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(action.colors))
                        .then(if (active) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                        .clickable(onClickLabel = action.label) { onDismiss(); action.onSelect() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Text(
                    action.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(x - 50.dp, y - ActionSize / 2 - 24.dp).width(100.dp),
                )
            }

            Box(
                Modifier.offset(centerX - FabSize / 2, centerY - FabSize / 2)
                    .size(FabSize)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1442)) // oscuro en todos los temas: la X es blanca
                    .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    .clickable(onClickLabel = "Cerrar menú", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

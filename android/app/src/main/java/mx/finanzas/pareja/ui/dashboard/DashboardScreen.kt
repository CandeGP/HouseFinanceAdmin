package mx.finanzas.pareja.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import mx.finanzas.pareja.ui.components.GlassCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import mx.finanzas.pareja.domain.SpendingSlice
import mx.finanzas.pareja.ui.AppTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.domain.formatMonthEs
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.domain.CardRank
import mx.finanzas.pareja.domain.NextPayment
import mx.finanzas.pareja.domain.Quincena
import mx.finanzas.pareja.domain.QuincenaTotals
import mx.finanzas.pareja.domain.RecurringCharge
import mx.finanzas.pareja.domain.dueLine
import mx.finanzas.pareja.domain.sortByDue
import mx.finanzas.pareja.domain.formatDayMonthEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.debtTotal
import mx.finanzas.pareja.ui.components.Amount
import mx.finanzas.pareja.ui.components.CardChip
import mx.finanzas.pareja.ui.components.UsageBar
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.ScopeToggle

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    memberNames: Map<String, String>,
    refreshKey: Int,
    onAddCard: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenInstallments: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenLoans: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }
    var openQuincena by remember { mutableStateOf<Pair<String, Quincena>?>(null) }

    openQuincena?.let { (title, quincena) ->
        val cardsById = state.summary.ranking.associate { it.view.card.id to it.view.card }
        AlertDialog(
            onDismissRequest = { openQuincena = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (quincena.payments.isEmpty()) Text("Nada pendiente en esta quincena.")
                    quincena.payments.sortedBy { it.dueDate }.forEach { payment ->
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    cardsById[payment.cardId]?.let { "${it.name} •••• ${it.last4}" } ?: "Tarjeta",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(formatMxn(payment.amount), fontWeight = FontWeight.Bold)
                            }
                            Text(
                                payment.dueLine(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (payment.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openQuincena = null }) { Text("Cerrar") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Resumen", style = MaterialTheme.typography.headlineMedium)
            ScopeToggle(scope = state.scope, onChange = vm::setScope)
        }
        ErrorText(state.error, Modifier.padding(horizontal = 20.dp))

        if (state.pendingCount > 0) {
            PendingBanner(state.pendingCount, onOpenInbox, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp))
        }

        when {
            state.loading && state.summary.ranking.isEmpty() ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            state.summary.ranking.isEmpty() ->
                EmptyState(
                    title = "Aún no hay tarjetas",
                    subtitle = "Agrega tus tarjetas con su día de corte y de pago para ver cuánto pagarás.",
                    actionLabel = "Agregar tarjeta",
                    onAction = onAddCard,
                )
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    QuincenaCards(state.quincenas, state.firstQuincenaActive) { title, quincena -> openQuincena = title to quincena }
                }
                if (state.monthSpend.isNotEmpty()) item { SpendCard(state.monthSpend) }
                if (state.loanTotals.owedToYou > 0 || state.loanTotals.youOwe > 0) item { LoansEntry(state.loanTotals, onOpenLoans) }
                item { InstallmentsEntry(onOpenInstallments) }
                item { DuplicatesEntry(onOpenDuplicates) }
                if (state.recurring.isNotEmpty()) item { RecurringCard(state.recurring) }
                item { Text("Tarjetas por pagar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp, start = 2.dp)) }
                items(sortByDue(state.summary.ranking, state.nextPayments), key = { it.view.card.id }) { rank ->
                    CardRankItem(
                        rank,
                        ownerName = memberNames[rank.view.card.ownerId],
                        payment = state.nextPayments[rank.view.card.id],
                        status = state.statuses[rank.view.card.id],
                    )
                }
            }
        }
    }
}

/** Acceso al calendario de mensualidades de compras a meses, mes por mes. */
@Composable
private fun InstallmentsEntry(onOpen: () -> Unit) {
    EntryRow(
        Icons.Outlined.CalendarMonth, AppTheme.tokens.chart[3],
        "Compras a meses", "Mensualidades de los próximos meses", onOpen,
    )
}

/** Lo que te deben y lo que debes (préstamos y cobros divididos con tu pareja o amigos). */
@Composable
private fun LoansEntry(totals: mx.finanzas.pareja.domain.LoanTotals, onOpen: () -> Unit) {
    val detail = buildList {
        if (totals.owedToYou > 0) add("Te deben ${formatMxn(totals.owedToYou)}")
        if (totals.youOwe > 0) add("Debes ${formatMxn(totals.youOwe)}")
    }.joinToString(" · ")
    EntryRow(Icons.Outlined.AccountBalance, AppTheme.tokens.chart[0], "Por cobrar", detail, onOpen)
}

/** Acceso a los cargos que parecen registrados dos veces. */
@Composable
private fun DuplicatesEntry(onOpen: () -> Unit) {
    EntryRow(
        Icons.Outlined.ContentCopy, AppTheme.tokens.chart[2],
        "Posibles duplicados", "Cargos que parecen registrados dos veces", onOpen,
    )
}

/** Lo que vence este mes, en dos bloques grandes: pagos del 1 al 15 y del 16 a fin de mes. */
@Composable
private fun QuincenaCards(
    totals: QuincenaTotals,
    firstActive: Boolean,
    onOpen: (title: String, quincena: Quincena) -> Unit,
) {
    val tokens = AppTheme.tokens
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val nextFirstEnd = totals.nextFirstEnd
        // La quincena en curso va en amarillo; la otra, en el estilo neutro con borde.
        QuincenaCard(
            title = if (nextFirstEnd != null) "Próximo mes a pagar 1ª quincena" else "A pagar 1ª quincena",
            range = if (nextFirstEnd != null) "Vence del 1 al ${formatDayMonthEs(nextFirstEnd)}" else "Vence del 1 al 15",
            quincena = totals.first,
            container = if (firstActive) tokens.quincenaSecond else tokens.quincenaFirst,
            content = if (firstActive) tokens.onQuincenaSecond else tokens.onQuincenaFirst,
            bordered = !firstActive,
            modifier = Modifier.weight(1f),
            onClick = { onOpen(if (nextFirstEnd != null) "Próximo mes a pagar 1ª quincena" else "A pagar 1ª quincena", totals.first) },
        )
        QuincenaCard(
            "A pagar 2ª quincena", "Vence del 16 a fin de mes", totals.second,
            if (firstActive) tokens.quincenaFirst else tokens.quincenaSecond,
            if (firstActive) tokens.onQuincenaFirst else tokens.onQuincenaSecond,
            Modifier.weight(1f),
            onClick = { onOpen("A pagar 2ª quincena", totals.second) },
            bordered = firstActive,
        )
    }
}

@Composable
private fun QuincenaCard(
    title: String,
    range: String,
    quincena: Quincena,
    container: Brush,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    bordered: Boolean = false,
) {
    // La primera quincena lleva borde fino (la amarilla no), como en el mockup.
    GlassCard(
        onClick = onClick,
        modifier = modifier.then(
            if (bordered) Modifier.border(1.dp, AppTheme.tokens.glassBorder, RoundedCornerShape(22.dp)) else Modifier,
        ),
        container = container,
        contentColor = content,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.alpha(0.85f))
            Amount(quincena.total, wholeSp = 25, centsSp = 15, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Text(range, fontSize = 12.sp, modifier = Modifier.alpha(0.78f))
            Text(
                when (quincena.cards) {
                    0 -> "Nada pendiente"
                    1 -> "1 tarjeta"
                    else -> "${quincena.cards} tarjetas"
                },
                fontSize = 12.sp,
                modifier = Modifier.alpha(0.78f),
            )
            if (quincena.overdue > 0) {
                Text(
                    "Incluye ${formatMxn(quincena.overdue)} vencido",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val MAX_RECURRING_SHOWN = 5

/** Cargos que se repiten cada mes y se esperan pronto, con lo que suman. */
@Composable
private fun RecurringCard(charges: List<RecurringCharge>) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Próximos cargos (estimado)", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatMxn(charges.sumOf { it.amount })} comprometidos",
                style = MaterialTheme.typography.titleLarge,
            )
            charges.take(MAX_RECURRING_SHOWN).forEach { charge ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        charge.merchant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "~${formatDayMonthEs(charge.nextExpected)} · ${formatMxn(charge.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (charges.size > MAX_RECURRING_SHOWN) {
                Text(
                    "y ${charges.size - MAX_RECURRING_SHOWN} más",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Cargos que se repiten cada mes en tus últimos gastos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardRankItem(rank: CardRank, ownerName: String?, payment: NextPayment?, status: CardCycleStatus?) {
    val card = rank.view.card
    val est = rank.view.estimate
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(card.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    CardChip(card, Modifier.padding(top = 6.dp))
                }
                // Lo que toca pagar (con el corte anterior sin pagar si lo hay), no el gasto del ciclo abierto.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMxn(payment?.amount ?: est.cycleTotal),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        "Pago del mes" + (ownerName?.let { " · de $it" } ?: ""),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
            }
            payment?.let {
                Text(
                    it.dueLine(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (it.overdue) MaterialTheme.colorScheme.error else AppTheme.tokens.warning,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            // Deuda total: corte anterior sin pagar + ciclo abierto + meses sin intereses que faltan.
            Text(
                "Debes en total: ${formatMxn(debtTotal(est, status))}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (status?.status == CardPayStatus.UNTRACKED) {
                Text(
                    "Corte anterior sin registro de pago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Mismo cálculo que "% de tu límite" en Tarjetas: deuda total entre el límite de crédito.
            val usagePct = if (est.creditLimit > 0) debtTotal(est, status) / est.creditLimit * 100 else 0.0
            UsageBar(
                fraction = (usagePct / 100).coerceIn(0.0, 1.0).toFloat(),
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "Uso: ${"%.1f".format(usagePct)}% de ${formatMxn(est.creditLimit)} · " +
                    "${"%.0f".format(rank.sharePct)}% del gasto total",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

private const val DONUT_MAX_SLICES = 4
private const val DONUT_GAP_DEGREES = 6f

/** Gasto del mes por categoría: dona con el total al lado y la leyenda de las categorías más grandes. */
@Composable
private fun SpendCard(slices: List<SpendingSlice>) {
    val total = slices.sumOf { it.total }
    // Las categorías chicas se juntan en «Otros» para que la dona y la leyenda se lean de un vistazo.
    val rest = slices.drop(DONUT_MAX_SLICES)
    val shown = slices.take(DONUT_MAX_SLICES) +
        if (rest.isEmpty()) emptyList() else listOf(SpendingSlice("Otros", rest.sumOf { it.total }, rest.sumOf { it.pct }))
    val colors = AppTheme.tokens.chart
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val monthName = formatMonthEs(today.year, today.monthNumber).substringBefore(' ')
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val description = "Gasto del mes por categoría: " +
                shown.joinToString(", ") { "${it.label} ${"%.0f".format(it.pct)}%" }
            Donut(
                values = shown.map { it.total.toFloat() },
                colors = colors,
                modifier = Modifier.size(124.dp).semantics { contentDescription = description },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Gastado en $monthName",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
                Amount(total, wholeSp = 24, centsSp = 15, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                // Leyenda en dos columnas, como en el mockup.
                shown.withIndex().chunked(2).forEach { pair ->
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (index, slice) ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(colors[index % colors.size]))
                                Text(
                                    slice.label,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(values: List<Float>, colors: List<Color>, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Canvas(modifier) {
        val stroke = size.minDimension * 0.13f
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        val sum = values.sum()
        if (sum <= 0f) return@Canvas
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value / sum * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = start + DONUT_GAP_DEGREES / 2,
                sweepAngle = (sweep - DONUT_GAP_DEGREES).coerceAtLeast(1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            start += sweep
        }
    }
}

/** Aviso rosa de cobros detectados por revisar (46 de alto, borde y fondo del mismo color). */
@Composable
private fun PendingBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pink = MaterialTheme.colorScheme.tertiary
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(16.dp),
        color = pink.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, pink.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(pink))
            Text(
                "$count cobro(s) detectado(s) por revisar",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** Fila de acceso con icono en recuadro de color, título, descripción y flecha. */
@Composable
private fun EntryRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 62.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

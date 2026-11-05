package mx.finanzas.pareja.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.finanzas.pareja.data.CARD_KIND_CREDIT
import mx.finanzas.pareja.data.CARD_KIND_DEBIT
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.domain.CARD_BRANDS
import mx.finanzas.pareja.domain.CARD_COLOR_CHOICES
import mx.finanzas.pareja.domain.CardDraft
import mx.finanzas.pareja.domain.CardEditDraft
import mx.finanzas.pareja.domain.CardField
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.GradientButton
import java.util.Locale

private val GroupShape = RoundedCornerShape(18.dp)
private val RowHeight = 40.dp

/**
 * Alta y edición de una tarjeta en la misma pantalla completa: vista previa en vivo, tipo, marca, datos,
 * color y botón fijo abajo. [initial] null = tarjeta nueva; con tarjeta, los campos vienen llenos y aparece Archivar.
 */
@Composable
fun CardForm(
    initial: Card?,
    errors: Map<CardField, String>,
    onDismiss: () -> Unit,
    onSave: (CardEditDraft) -> Unit,
    onArchive: (() -> Unit)? = null,
) {
    var draft by remember(initial?.id) {
        mutableStateOf(
            initial?.let {
                CardDraft(
                    name = it.name, bank = it.bank, last4 = it.last4,
                    limitText = if (it.isDebit) "" else plainNumber(it.creditLimit),
                    cutoffText = if (it.isDebit) "" else it.cutoffDay.toString(),
                    dueText = if (it.isDebit) "" else it.dueDay.toString(),
                    color = it.color, kind = it.kind, brand = it.brand,
                )
            } ?: CardDraft(),
        )
    }
    var budgetText by remember(initial?.id) { mutableStateOf(initial?.monthlyBudget?.let(::plainNumber).orEmpty()) }
    var hasCashback by remember(initial?.id) { mutableStateOf(initial?.hasCashback ?: false) }
    val debit = draft.kind == CARD_KIND_DEBIT
    val preview = (initial ?: Card(
        id = "preview", householdId = "", ownerId = "", name = "", bank = "", last4 = "",
        creditLimit = 0.0, cutoffDay = 1, dueDay = 1,
    )).copy(
        name = draft.name.ifBlank { "Mi tarjeta" },
        bank = draft.bank.ifBlank { "Banco" },
        last4 = draft.last4.padEnd(4, '0'),
        color = draft.color, kind = draft.kind, brand = draft.brand,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(AppTheme.tokens.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(44.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancelar") }
                    Text(
                        if (initial == null) "Nueva tarjeta" else "Editar tarjeta",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center),
                    )
                    if (onArchive != null) {
                        TextButton(onClick = onArchive, modifier = Modifier.align(Alignment.CenterEnd)) {
                            Text("Archivar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                CardFace(preview, front = true, onClick = null, modifier = Modifier.fillMaxWidth().height(168.dp))

                Segmented(
                    options = listOf(CARD_KIND_CREDIT to "Crédito", CARD_KIND_DEBIT to "Débito"),
                    selected = draft.kind,
                    onSelect = { draft = draft.copy(kind = it) },
                )

                SectionLabel("Marca")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CARD_BRANDS.forEach { brand ->
                        BrandChip(
                            label = brandLabel(brand), selected = draft.brand == brand,
                            onClick = { draft = draft.copy(brand = if (draft.brand == brand) null else brand) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Group(errors.filterKeys { it in setOf(CardField.NAME, CardField.BANK, CardField.LAST4) }) {
                    FormRow("Apodo", draft.name, { draft = draft.copy(name = it) }, "Ej. BBVA Azul", error = errors[CardField.NAME])
                    FormRow("Banco", draft.bank, { draft = draft.copy(bank = it) }, "Ej. BBVA", error = errors[CardField.BANK])
                    FormRow(
                        "Últimos 4", draft.last4, { draft = draft.copy(last4 = it.filter(Char::isDigit).take(4)) }, "1234",
                        KeyboardType.Number, error = errors[CardField.LAST4],
                    )
                    if (initial != null && initial.altLast4.isNotEmpty()) {
                        RowShell {
                            Text("También reconoce", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                initial.altLast4.joinToString("  ·  "), modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Group(errors.filterKeys { it in setOf(CardField.LIMIT, CardField.CUTOFF, CardField.DUE, CardField.BUDGET) }) {
                    if (!debit) {
                        FormRow(
                            "Límite de crédito", draft.limitText, { draft = draft.copy(limitText = it) }, "30000",
                            KeyboardType.Decimal, error = errors[CardField.LIMIT],
                        )
                        RowShell {
                            HalfField("Corte", draft.cutoffText, { draft = draft.copy(cutoffText = it.filter(Char::isDigit).take(2)) }, errors[CardField.CUTOFF])
                            Box(Modifier.size(width = 1.dp, height = 22.dp).background(AppTheme.tokens.glassBorder))
                            HalfField("Pago", draft.dueText, { draft = draft.copy(dueText = it.filter(Char::isDigit).take(2)) }, errors[CardField.DUE])
                        }
                    }
                    FormRow(
                        "Presupuesto mensual", budgetText, { budgetText = it }, "Opcional",
                        KeyboardType.Decimal, error = errors[CardField.BUDGET],
                    )
                    RowShell {
                        Text("Da cashback", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Switch(checked = hasCashback, onCheckedChange = { hasCashback = it })
                    }
                }
                Text(
                    "Solo guardamos apodo y últimos 4 dígitos.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp),
                )

                SectionLabel("Color")
                errors[CardField.COLOR]?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (listOf<String?>(null) + CARD_COLOR_CHOICES).forEach { hex ->
                        ColorDot(hex, selected = draft.color.equals(hex, ignoreCase = true), onClick = { draft = draft.copy(color = hex) })
                    }
                }
            }
            GradientButton(
                onClick = { onSave(CardEditDraft(draft, budgetText, draft.color, hasCashback)) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            ) { Text(if (initial == null) "Guardar tarjeta" else "Guardar cambios") }
        }
    }
}

private fun brandLabel(brand: String): String = when (brand) {
    "visa" -> "Visa"
    "mastercard" -> "Mastercard"
    else -> "Amex"
}

private fun plainNumber(value: Double): String = "%.2f".format(Locale.US, value)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppTheme.tokens.glass)
            .border(1.dp, AppTheme.tokens.glassBorder, RoundedCornerShape(18.dp)).padding(4.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .semantics { role = Role.RadioButton; this.selected = on }
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun BrandChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(38.dp).clip(shape).background(if (selected) Color(0x338F7CFF) else AppTheme.tokens.glass)
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF8F7CFF) else AppTheme.tokens.glassBorder, shape)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

/** Grupo de filas con fondo de vidrio; los errores de sus campos salen debajo, para que un rechazo no sea mudo. */
@Composable
private fun Group(errors: Map<CardField, String>, content: @Composable () -> Unit) {
    Column {
        Column(
            Modifier.fillMaxWidth().clip(GroupShape).background(AppTheme.tokens.glass)
                .border(BorderStroke(1.dp, AppTheme.tokens.glassBorder), GroupShape).padding(horizontal = 16.dp),
        ) { content() }
        errors.values.firstOrNull()?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

@Composable
private fun RowShell(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun FormRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
) {
    RowShell {
        Text(
            label, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            color = if (error != null) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
        RowInput(value, onChange, placeholder, keyboard, label, Modifier.weight(1f))
    }
}

@Composable
private fun RowScope.HalfField(label: String, value: String, onChange: (String) -> Unit, error: String?) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            color = if (error != null) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
        RowInput(value, onChange, "1-31", KeyboardType.Number, label, Modifier.weight(1f))
    }
}

@Composable
private fun RowInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType,
    description: String,
    modifier: Modifier,
) {
    val style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, textAlign = TextAlign.End)
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.CenterEnd) {
        if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 15.sp, textAlign = TextAlign.End)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true, textStyle = style,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColorDot(hex: String?, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .background(if (hex == null) AppTheme.tokens.glass else Color(android.graphics.Color.parseColor(hex)))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else AppTheme.tokens.glassBorder, CircleShape)
            .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = if (hex == null) "Color automático" else "Color $hex" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (hex == null) Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

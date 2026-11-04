package mx.finanzas.pareja.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.NewCard
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.CardDraft
import mx.finanzas.pareja.domain.CardDraftResult
import mx.finanzas.pareja.domain.CardField
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.account.AppearanceCard
import mx.finanzas.pareja.ui.cards.CardFace
import mx.finanzas.pareja.ui.components.AuthField
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.components.StepHeader

/**
 * Pasos 3 a 5 del onboarding, para quien acaba de crear su hogar: primera tarjeta, apariencia y un recorrido
 * rápido del botón «+». [onFinish] recibe si se quiere abrir los tutoriales al terminar.
 */
@Composable
fun FirstRunFlow(
    repo: FinanceRepository,
    userId: String,
    householdId: String,
    name: String,
    onFinish: (openTutorials: Boolean) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(3) }
    when (step) {
        3 -> FirstCardStep(repo, userId, householdId, onNext = { step = 4 })
        4 -> ThemeStep(onNext = { step = 5 })
        else -> DoneStep(name, onFinish)
    }
}

/** Fondo con resplandores y columna desplazable que llena al menos toda la pantalla. */
@Composable
private fun OnboardingPage(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            val top = Offset(size.width * 0.95f, 0f)
            val bottom = Offset(0f, size.height * 0.85f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x668B7CFF), Color.Transparent), center = top, radius = size.width * 0.75f),
                radius = size.width * 0.75f,
                center = top,
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0x403DD6D0), Color.Transparent), center = bottom, radius = size.width * 0.8f),
                radius = size.width * 0.8f,
                center = bottom,
            )
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun FirstCardStep(repo: FinanceRepository, userId: String, householdId: String, onNext: () -> Unit) {
    var draft by remember { mutableStateOf(CardDraft()) }
    var errors by remember { mutableStateOf<Map<CardField, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val preview = Card(
        id = "preview", householdId = "", ownerId = "",
        name = draft.name.ifBlank { "Mi tarjeta" },
        bank = draft.bank.ifBlank { "Banco" },
        last4 = draft.last4.padEnd(4, '0'),
        creditLimit = 0.0, cutoffDay = 1, dueDay = 1, color = draft.color,
    )

    fun save() {
        when (val result = draft.validate()) {
            is CardDraftResult.Invalid -> errors = result.errors
            is CardDraftResult.Valid -> scope.launch {
                saving = true
                message = null
                val v = result.value
                try {
                    repo.addCard(
                        NewCard(
                            householdId = householdId, ownerId = userId, name = v.name, bank = v.bank, last4 = v.last4,
                            creditLimit = v.creditLimit, cutoffDay = v.cutoffDay, dueDay = v.dueDay, color = v.color,
                        ),
                    )
                    onNext()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = userMessage(e)
                } finally {
                    saving = false
                }
            }
        }
    }

    OnboardingPage {
        StepHeader(step = 3, total = 4)
        Text("Agrega tu primera tarjeta", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 22.dp))
        Text(
            "Con el corte y el pago te avisamos cuándo pagar. Puedes cambiarlo después.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        CardFace(preview, front = true, onClick = null, modifier = Modifier.fillMaxWidth().height(160.dp))
        Spacer(Modifier.height(16.dp))
        AuthField("Apodo", draft.name, { draft = draft.copy(name = it) }, placeholder = "Ej. BBVA Azul", error = errors[CardField.NAME])
        Spacer(Modifier.height(12.dp))
        AuthField("Banco", draft.bank, { draft = draft.copy(bank = it) }, placeholder = "Ej. BBVA", error = errors[CardField.BANK])
        Spacer(Modifier.height(12.dp))
        AuthField(
            "Últimos 4 dígitos", draft.last4, { draft = draft.copy(last4 = it.filter(Char::isDigit).take(4)) },
            placeholder = "1234", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), error = errors[CardField.LAST4],
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            "Límite de crédito", draft.limitText, { draft = draft.copy(limitText = it) },
            placeholder = "30000", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), error = errors[CardField.LIMIT],
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthField(
                "Día de corte", draft.cutoffText, { draft = draft.copy(cutoffText = it.filter(Char::isDigit).take(2)) },
                modifier = Modifier.weight(1f), placeholder = "1-31",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), error = errors[CardField.CUTOFF],
            )
            AuthField(
                "Día de pago", draft.dueText, { draft = draft.copy(dueText = it.filter(Char::isDigit).take(2)) },
                modifier = Modifier.weight(1f), placeholder = "1-31",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), error = errors[CardField.DUE],
            )
        }
        ErrorText(message, Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(20.dp))
        GradientButton(onClick = ::save, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "Guardando…" else "Guardar y continuar")
        }
        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Omitir por ahora") }
    }
}

@Composable
private fun ThemeStep(onNext: () -> Unit) {
    OnboardingPage {
        StepHeader(step = 4, total = 4)
        Text("Hazla tuya", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 22.dp))
        Text(
            "Elige cómo se ve. Los avisos de corte, el bloqueo con huella y la captura de cobros se activan en Cuenta.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        AppearanceCard()
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(20.dp))
        GradientButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Terminar") }
    }
}

@Composable
private fun DoneStep(name: String, onFinish: (openTutorials: Boolean) -> Unit) {
    val tokens = AppTheme.tokens
    Box(
        Modifier.fillMaxSize().drawBehind {
            val center = Offset(size.width / 2, size.height * 0.22f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x474ADE80), Color.Transparent), center = center, radius = size.width * 0.7f),
                radius = size.width * 0.7f,
                center = center,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(72.dp))
            Box(
                Modifier.size(92.dp).shadow(18.dp, CircleShape, ambientColor = Color(0x734ADE80), spotColor = Color(0x734ADE80))
                    .clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF3DD6D0)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp)) }
            Text(
                "Todo listo, $name",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 30.dp),
            )
            Text(
                "Tu hogar está listo. Este botón será tu mejor amigo:",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                modifier = Modifier.padding(top = 10.dp),
            )
            Column(
                Modifier.padding(top = 22.dp).fillMaxWidth().shadow(18.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp),
            ) {
                Text("El botón «+»", color = Color(0xFF1F1747), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Un toque registra un cobro. Mantenlo presionado para pago de tarjeta, escanear ticket o préstamo, " +
                        "y desliza hasta la opción para elegirla.",
                    color = Color(0xFF1F1747),
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Box(
                Modifier.padding(top = 16.dp).size(72.dp)
                    .border(5.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                    .padding(6.dp).clip(CircleShape).background(tokens.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, contentDescription = null, tint = tokens.onAccent, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.weight(1f))
            GradientButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) { Text("Ir al Resumen") }
            TextButton(onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text("Ver tutoriales") }
        }
    }
}

package mx.finanzas.pareja.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.NewLoan
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.SplitDraft
import mx.finanzas.pareja.domain.SplitField
import mx.finanzas.pareja.domain.SplitResult
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.AuthField
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.components.SectionLabel
import mx.finanzas.pareja.ui.transactions.AmountInput

/**
 * «Dividir cobro»: pagaste algo y a tu pareja le toca una parte. Se guarda como un préstamo a [partnerName]
 * (lo ves en Préstamos como "te debe"), sin tablas nuevas.
 */
@Composable
fun SplitScreen(
    repo: FinanceRepository,
    userId: String,
    householdId: String,
    partnerName: String?,
    today: LocalDate,
    onDone: (saved: Boolean) -> Unit,
) {
    var draft by remember { mutableStateOf(SplitDraft()) }
    var errors by remember { mutableStateOf<Map<SplitField, String>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        when (val result = draft.validate()) {
            is SplitResult.Invalid -> errors = result.errors
            is SplitResult.Valid -> scope.launch {
                errors = emptyMap()
                saving = true
                message = null
                try {
                    repo.addLoan(
                        NewLoan(
                            householdId = householdId, ownerId = userId, person = partnerName.orEmpty(),
                            direction = LoanDirection.LENT, amount = result.partnerShare, occurredOn = today,
                            method = PaymentMethod.TRANSFER,
                            note = "Dividido: ${result.concept} (total ${formatMxn(result.total)})",
                        ),
                    )
                    onDone(true)
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

    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    TextButton(onClick = { onDone(false) }, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancelar") }
                    Text("Dividir cobro", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
                }
                if (partnerName == null) {
                    EmptyState(
                        title = "Aún no tienes pareja en el hogar",
                        subtitle = "Invítala desde Cuenta y luego podrán dividir cobros.",
                    )
                    return@Column
                }
                ErrorText(message)
                AmountInput(
                    value = draft.totalText,
                    onChange = { draft = draft.copy(totalText = it) },
                    enabled = !saving,
                    error = errors[SplitField.TOTAL],
                )
                AuthField(
                    label = "¿Qué se pagó?",
                    value = draft.concept,
                    onValueChange = { draft = draft.copy(concept = it) },
                    placeholder = "Ej. Cena, súper",
                    enabled = !saving,
                    error = errors[SplitField.CONCEPT],
                )
                SectionLabel("LE TOCA A ${partnerName.uppercase()}")
                AuthField(
                    label = "Su parte (vacío = la mitad)",
                    value = draft.partnerShareText,
                    onValueChange = { draft = draft.copy(partnerShareText = it) },
                    placeholder = "Mitad",
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    error = errors[SplitField.SHARE],
                )
                Text(
                    "Se guarda como un préstamo a $partnerName: lo ves en Préstamos como lo que te debe, y puede abonar hasta liquidarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (partnerName != null) {
                GradientButton(
                    onClick = ::save,
                    enabled = !saving,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                ) { Text(if (saving) "Guardando…" else "Dividir") }
            }
        }
    }
}

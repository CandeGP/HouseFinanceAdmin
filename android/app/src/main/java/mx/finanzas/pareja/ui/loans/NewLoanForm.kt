package mx.finanzas.pareja.ui.loans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.LoanDraft
import mx.finanzas.pareja.domain.LoanField
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.AuthField
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.components.PastDateButton
import mx.finanzas.pareja.ui.components.SectionLabel
import mx.finanzas.pareja.ui.components.SelectChip
import mx.finanzas.pareja.ui.payments.PayeeField
import mx.finanzas.pareja.ui.transactions.AmountInput

/**
 * Formulario de préstamo nuevo a pantalla completa, con el mismo estilo que Registrar pago y Dividir cobro:
 * monto grande, campos con etiqueta arriba y botón con degradado fijo abajo.
 */
@Composable
internal fun NewLoanForm(
    initialPerson: String,
    people: List<String>,
    errors: Map<LoanField, String>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (LoanDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(LoanDraft(person = initialPerson)) }
    // Al volver a escribir se quita el error anterior.
    LaunchedEffect(draft) { if (errors.isNotEmpty()) onEdit() }
    BackHandler(onBack = onDismiss)

    // Cubre la pantalla de Préstamos: el degradado de la app va detrás.
    Box(Modifier.fillMaxSize().background(AppTheme.tokens.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancelar") }
                Text("Nuevo préstamo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Le presté", draft.direction == LoanDirection.LENT, { draft = draft.copy(direction = LoanDirection.LENT) })
                SelectChip(
                    "Me prestaron",
                    draft.direction == LoanDirection.BORROWED,
                    { draft = draft.copy(direction = LoanDirection.BORROWED) },
                )
            }
            AmountInput(
                value = draft.amountText,
                onChange = { draft = draft.copy(amountText = it) },
                enabled = true,
                error = errors[LoanField.AMOUNT],
            )
            PayeeField(
                value = draft.person,
                payees = people,
                enabled = true,
                onChange = { draft = draft.copy(person = it) },
                label = "Persona",
                error = errors[LoanField.PERSON],
            )
            PastDateButton(draft.date, today, onChange = { draft = draft.copy(date = it) })
            errors[LoanField.DATE]?.let { ErrorText(it) }
            SectionLabel("FORMA DE PAGO")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Transferencia", draft.method == PaymentMethod.TRANSFER, { draft = draft.copy(method = PaymentMethod.TRANSFER) })
                SelectChip("Efectivo", draft.method == PaymentMethod.CASH, { draft = draft.copy(method = PaymentMethod.CASH) })
            }
            AuthField(
                label = "Nota (opcional)",
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it) },
                singleLine = false,
            )
        }
        GradientButton(
            onClick = { onSave(draft) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        ) { Text("Guardar préstamo") }
    }
}

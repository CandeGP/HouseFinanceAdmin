package mx.finanzas.pareja.ui.auth

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.components.StepHeader

/** Recuerda si ya se mostró la bienvenida en este teléfono (solo la ve quien instala la app y aún no entra). */
object WelcomePrefs {
    private const val PREFS = "onboarding"
    private const val KEY_SEEN = "welcome_seen"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seen(context: Context): Boolean = prefs(context).getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_SEEN, true).apply()
    }
}

/** Paso 1 del onboarding: qué hace la app, con tarjetas apiladas de adorno y tres ventajas. */
@Composable
fun WelcomeScreen(onStart: () -> Unit, onSignIn: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            val top = Offset(size.width * 0.95f, 0f)
            val bottom = Offset(0f, size.height * 0.85f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x808B7CFF), Color.Transparent), center = top, radius = size.width * 0.75f),
                radius = size.width * 0.75f,
                center = top,
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0x4D3DD6D0), Color.Transparent), center = bottom, radius = size.width * 0.8f),
                radius = size.width * 0.8f,
                center = bottom,
            )
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 16.dp),
        ) {
            StepHeader(step = 1, total = 4)
            Hero(Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp))
            Text(
                "Las finanzas de la casa, claras para los dos",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "Sabe qué pagar cada quincena, divide cobros y deja que la app detecte tus cargos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
            )
            Benefit(Icons.Outlined.CalendarMonth, Color(0xFF8B7CFF), "Tus pagos por quincena, siempre a la vista")
            Spacer(Modifier.height(10.dp))
            Benefit(Icons.Outlined.Groups, Color(0xFF3DD6D0), "Una casa compartida para tu pareja y tú")
            Spacer(Modifier.height(10.dp))
            Benefit(Icons.Outlined.Email, Color(0xFFFFB547), "Cargos detectados desde tus avisos y estados de cuenta")
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(20.dp))
            GradientButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Comenzar") }
            Text(
                buildAnnotatedString {
                    append("¿Ya tienes cuenta? ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append("Inicia sesión") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSignIn).padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun Hero(modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.size(width = 300.dp, height = 220.dp)) {
        Box(
            Modifier.offset(x = 14.dp, y = 30.dp).size(width = 250.dp, height = 150.dp).rotate(-9f).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFFF5B94A), Color(0xFFD98A1C)))),
        )
        Box(
            Modifier.offset(x = 28.dp, y = 40.dp).size(width = 250.dp, height = 150.dp).rotate(-2f).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFFFF6FA8), Color(0xFFD6246E)))),
        )
        Column(
            Modifier.offset(x = 42.dp, y = 52.dp).size(width = 250.dp, height = 150.dp).rotate(6f)
                .shadow(18.dp, shape).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF5B9BFF), Color(0xFF2F63E0))))
                .padding(18.dp),
        ) {
            Text("Casa de dos", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(26.dp))
            Box(Modifier.size(width = 150.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.85f)))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(width = 96.dp, height = 9.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.5f)))
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(64.dp)
                .shadow(14.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x8C7C6CFF), spotColor = Color(0x8C7C6CFF))
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF7C6CFF), Color(0xFFFF6FA8)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun Benefit(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
        Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

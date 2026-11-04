package mx.finanzas.pareja.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.domain.cardAccentArgb

private val FaceHeight = 210.dp
private val PeekHeight = 56.dp
private val FaceShape = RoundedCornerShape(26.dp)

/**
 * Tus tarjetas apiladas como en una wallet: las de atrás asoman con su nombre y la elegida va al frente
 * completa. Tocar una la trae al frente.
 */
@Composable
fun WalletStack(
    cards: List<Card>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(cards, selectedId) {
        cards.filter { it.id != selectedId } + cards.filter { it.id == selectedId }
    }
    Box(modifier.fillMaxWidth().height(FaceHeight + PeekHeight * (ordered.size - 1))) {
        ordered.forEachIndexed { index, card ->
            key(card.id) {
                val top by animateDpAsState(PeekHeight * index, label = "wallet-offset")
                CardFace(
                    card = card,
                    front = index == ordered.lastIndex,
                    onClick = { onSelect(card.id) },
                    modifier = Modifier.offset(y = top).zIndex(index.toFloat()).fillMaxWidth().height(FaceHeight),
                )
            }
        }
    }
}

/** La tarjeta con su color: nombre con inicial y últimos 4; la de al frente se ve completa (chip, número y banco). */
@Composable
internal fun CardFace(card: Card, front: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val base = Color(cardAccentArgb(card))
    val brush = Brush.linearGradient(listOf(lerp(base, Color.White, 0.18f), lerp(base, Color.Black, 0.18f)))
    // Texto oscuro sobre colores claros (amarillo, naranja) para que siempre se lea.
    val ink = if (base.luminance() > 0.5f) Color(0xFF1A1442) else Color.White
    Box(
        modifier
            .shadow(if (front) 10.dp else 4.dp, FaceShape)
            .clip(FaceShape)
            .background(brush)
            .drawBehind {
                if (front) {
                    // Círculo decorativo arriba a la derecha, como en el mockup.
                    drawCircle(Color.White.copy(alpha = 0.14f), radius = 110.dp.toPx(), center = Offset(size.width - 50.dp.toPx(), 40.dp.toPx()))
                }
            }
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Ver ${card.name}", onClick = onClick) else Modifier),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = if (front) 18.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(ink.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(card.name.take(1).uppercase(), color = ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    card.name,
                    color = ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (front) if (card.frozen) "Congelada" else "Crédito" else "•••• ${card.last4}",
                    color = ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (front) {
                Box(
                    Modifier.padding(top = 26.dp).size(width = 44.dp, height = 32.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF3F3F3), Color(0xFFC9C9C9)))),
                )
                Box(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(
                        "•••• •••• •••• ${card.last4}",
                        color = ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        card.bank,
                        color = ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

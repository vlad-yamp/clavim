package com.dodisrael.clavim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dodisrael.clavim.ui.theme.HeaderEnd
import com.dodisrael.clavim.ui.theme.HeaderStart

@Composable
fun AppHeader(
    title: String,
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit = {},
    onSettingsClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(colors = listOf(HeaderStart, HeaderEnd)),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = if (showBack) (if (compact) 4.dp else 12.dp) else 24.dp)
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
        }
        if (!showBack && onSettingsClick != null) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (showBack) 0.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showBack) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(73.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 44.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (compact) 17.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            if (!compact) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPhotoViewer(photos: List<String>, initialIndex: Int = 0, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photos[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onDismiss() },
                    contentScale = ContentScale.Fit
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (photos.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MenuCard(item: MenuItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(item.iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1B1F),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun CuteDog(modifier: Modifier = Modifier) {
    val fur       = Color(0xFFD4943E)
    val ear       = Color(0xFF9A5C10)
    val earInner  = Color(0xFFE8A8A8)
    val muzzle    = Color(0xFFF5DFB0)
    val eyePupil  = Color(0xFF1A0800)
    val noseColor = Color(0xFF200800)
    val tongue    = Color(0xFFFF7096)
    val cheek     = Color(0xFFFF8080)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = minOf(cx, cy) * 0.82f

        fun flopEar(flip: Float) {
            val path = Path().apply {
                moveTo(cx + flip * r * 0.45f, cy - r * 0.65f)
                cubicTo(cx + flip * r * 1.10f, cy - r * 0.95f, cx + flip * r * 1.25f, cy + r * 0.25f, cx + flip * r * 0.70f, cy + r * 0.38f)
                cubicTo(cx + flip * r * 0.40f, cy + r * 0.45f, cx + flip * r * 0.20f, cy - r * 0.10f, cx + flip * r * 0.45f, cy - r * 0.65f)
                close()
            }
            drawPath(path, ear)
            val inner = Path().apply {
                moveTo(cx + flip * r * 0.48f, cy - r * 0.50f)
                cubicTo(cx + flip * r * 0.88f, cy - r * 0.68f, cx + flip * r * 0.95f, cy + r * 0.12f, cx + flip * r * 0.68f, cy + r * 0.22f)
                cubicTo(cx + flip * r * 0.52f, cy + r * 0.28f, cx + flip * r * 0.38f, cy - r * 0.05f, cx + flip * r * 0.48f, cy - r * 0.50f)
                close()
            }
            drawPath(inner, earInner.copy(alpha = 0.45f))
        }
        flopEar(-1f); flopEar(+1f)

        drawCircle(fur, r, Offset(cx, cy))
        drawCircle(cheek.copy(alpha = 0.30f), r * 0.20f, Offset(cx - r * 0.65f, cy + r * 0.08f))
        drawCircle(cheek.copy(alpha = 0.30f), r * 0.20f, Offset(cx + r * 0.65f, cy + r * 0.08f))
        drawOval(muzzle, topLeft = Offset(cx - r * 0.40f, cy + r * 0.14f), size = Size(r * 0.80f, r * 0.52f))

        fun eyebrow(ex: Float) {
            drawArc(color = ear, startAngle = 195f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(ex - r * 0.17f, cy - r * 0.44f), size = Size(r * 0.34f, r * 0.20f),
                style = Stroke(r * 0.055f, cap = StrokeCap.Round))
        }
        eyebrow(cx - r * 0.30f); eyebrow(cx + r * 0.30f)

        fun eye(ex: Float) {
            drawCircle(Color.White, r * 0.165f, Offset(ex, cy - r * 0.10f))
            drawCircle(eyePupil,    r * 0.105f, Offset(ex + r * 0.02f, cy - r * 0.08f))
            drawCircle(Color.White, r * 0.040f, Offset(ex + r * 0.06f, cy - r * 0.14f))
        }
        eye(cx - r * 0.30f); eye(cx + r * 0.30f)

        val nosePath = Path().apply {
            moveTo(cx, cy + r * 0.20f)
            cubicTo(cx - r * 0.16f, cy + r * 0.13f, cx - r * 0.16f, cy + r * 0.27f, cx, cy + r * 0.27f)
            cubicTo(cx + r * 0.16f, cy + r * 0.27f, cx + r * 0.16f, cy + r * 0.13f, cx, cy + r * 0.20f)
        }
        drawPath(nosePath, noseColor)
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.035f, Offset(cx + r * 0.08f, cy + r * 0.17f))

        drawArc(color = noseColor, startAngle = 150f, sweepAngle = 70f, useCenter = false,
            topLeft = Offset(cx - r * 0.40f, cy + r * 0.22f), size = Size(r * 0.34f, r * 0.18f),
            style = Stroke(r * 0.048f, cap = StrokeCap.Round))
        drawArc(color = noseColor, startAngle = -20f, sweepAngle = 70f, useCenter = false,
            topLeft = Offset(cx + r * 0.06f, cy + r * 0.22f), size = Size(r * 0.34f, r * 0.18f),
            style = Stroke(r * 0.048f, cap = StrokeCap.Round))

        val tonguePath = Path().apply {
            moveTo(cx - r * 0.17f, cy + r * 0.37f)
            cubicTo(cx - r * 0.24f, cy + r * 0.37f, cx - r * 0.28f, cy + r * 0.60f, cx, cy + r * 0.60f)
            cubicTo(cx + r * 0.28f, cy + r * 0.60f, cx + r * 0.24f, cy + r * 0.37f, cx + r * 0.17f, cy + r * 0.37f)
            close()
        }
        drawPath(tonguePath, tongue)
        drawLine(color = tongue.copy(alpha = 0.55f), start = Offset(cx, cy + r * 0.38f),
            end = Offset(cx, cy + r * 0.59f), strokeWidth = r * 0.032f, cap = StrokeCap.Round)
    }
}

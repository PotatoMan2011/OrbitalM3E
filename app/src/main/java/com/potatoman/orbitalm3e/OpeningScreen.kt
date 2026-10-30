package com.potatoman.orbitalm3e

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.potatoman.orbitalm3e.ui.theme.OrbitalM3ETheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTextApi::class)
val Nunito = FontFamily(
    Font(
        R.font.nunito_italic,
        weight = FontWeight.ExtraBold,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)
val monospace = FontFamily(
    Font(
    R.font.monospace
    )
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpeningScreen(onFinished: () -> Unit) {
    // each logo gets its own expressive shape
    val logos = listOf(
        R.drawable.mawhiba to MaterialShapes.Cookie12Sided,
        R.drawable.maarif to MaterialShapes.Flower,
        R.drawable.nooralislam to MaterialShapes.Cookie12Sided,
    )

    val logoAnims = remember { logos.map { Animatable(0f) } }
    val titleAnim = remember { Animatable(0f) }
    val subtitleAnim = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }

    // slow spin for the shapes behind the logos
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "spinAngle"
    )

    LaunchedEffect(Unit) {
        logoAnims.forEachIndexed { i, anim ->
            launch {
                delay(i * 180L)
                anim.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow))
            }
        }

        delay(750)
        launch {
            titleAnim.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
        }
        delay(200)
        subtitleAnim.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))

        delay(5000)

        screenAlpha.animateTo(0f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .graphicsLayer {
                alpha = screenAlpha.value
                val s = 0.92f + 0.08f * screenAlpha.value
                scaleX = s; scaleY = s
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "Presented for ibda'a\nSupported by Maarif Education | Noor al islam Highschool",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.enterUp(titleAnim.value)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                logos.forEachIndexed { i, (res, polygon) ->
                    val v = logoAnims[i].value
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                alpha = v.coerceIn(0f, 1f)
                                val s = 0.4f + 0.6f * v   // overshoots past 1 = bounce
                                scaleX = s; scaleY = s
                                rotationZ = (1f - v) * -25f
                            }
                    ) {
                        // spinning cookie / flower background
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = if (i % 2 == 0) spin else -spin }
                                .clip(polygon.toShape())
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                        // logo stays still on top
                        Image(
                            painter = painterResource(res),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }
            }

            Text(
                "Mawhiba · Ibdaa",
                fontFamily = Nunito,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.enterUp(titleAnim.value)
            )

            Text(
                "OrbitalM3E — Made by Muhammad Fawwaz",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.enterUp(subtitleAnim.value)
            )

            LoadingIndicator(modifier = Modifier.enterUp(subtitleAnim.value))
        }
    }
}

/** Fades in and slides up, driven by a 0→1 progress value */
private fun Modifier.enterUp(progress: Float) = graphicsLayer {
    alpha = progress.coerceIn(0f, 1f)
    translationY = (1f - progress) * 60f
}

@Preview(showBackground = true)
@Composable
fun OpeningScreenPreview() {
    OrbitalM3ETheme {
        OpeningScreen(onFinished = {})
    }
}

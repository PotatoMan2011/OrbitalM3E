package com.potatoman.orbitalm3e

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Colors
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp

const val MAX_PROTONS = 36
const val MAX_NEUTRONS = 39
const val MAX_ELECTRONS = 36

// Meters. x = right, y = up, z = toward the camera
private val NUCLEUS_POSITION = Position(0f, 0f, 0f)
private val PROTON_COLOR = Color(0xFF030EDC)
private val NEUTRON_COLOR = Color(0xFF9E9E9E)
private val ELECTRON_COLOR = Color(0xFFFF0000)

val MyCustomFont = FontFamily(
    Font(R.font.nunito_italic),
)

private const val NUCLEON_SIZE = 0.14f
private const val ELECTRON_SIZE = 0.1f
private const val SPHERE = "3DModels/sphere.glb"

// One ring of electrons. speed in rad/s, tilt in degrees
private data class Subshell(
    val name: String,
    val capacity: Int,
    val radius: Float,
    val speed: Float,
    val tilt: Float
)

private val SUBSHELLS = listOf(
    Subshell("1s", capacity = 2, radius = 0.5f, speed = 1.6f, tilt = 0f),
    Subshell("2s", capacity = 2, radius = 0.75f, speed = 1.3f, tilt = 30f),
    Subshell("2p", capacity = 6, radius = 0.95f, speed = 1.1f, tilt = -50f),
    Subshell("3s", capacity = 2, radius = 1.15f, speed = 0.9f, tilt = 70f),
    Subshell("3p", capacity = 6, radius = 1.35f, speed = 0.75f, tilt = -20f),
    /*Subshell("4s", capacity = 2, radius = 1.55f, speed = 0.6f, tilt = 45f),
    Subshell("3d", capacity = 10,radius = 1.60f, speed = 0.5f, tilt = 60f),
    Subshell("4p", capacity = 6, radius = 1.75f, speed = 0.45f, tilt = -35f),
    Subshell("5s", capacity = 2, radius = 1.85f, speed = 0.4f, tilt = 80f),
    Subshell("4d", capacity = 10, radius = 1.9f, speed = 0.38f, tilt = -20f),
    Subshell("5p", capacity = 6, radius = 2f, speed = 0.35f, tilt = 90f),

     */
)

private fun fillSubshells(electrons: Int): List<Int> {
    var remaining = electrons
    return SUBSHELLS.map { shell ->
        val count = minOf(shell.capacity, remaining)
        remaining -= count
        count
    }
}

private fun ModelInstance.setColor(color: Color) {
    materialInstances.forEach {
        it.setParameter("baseColorFactor", Colors.RgbaType.SRGB, color.red, color.green, color.blue, color.alpha)
    }
}

private fun nucleonOffset(k: Int, total: Int): Position {
    if (total <= 1) return Position(0f, 0f, 0f)
    val ballRadius = NUCLEON_SIZE * 0.55f * cbrt(total.toFloat())
    val r = ballRadius * cbrt(k.toFloat() / total)
    val y = 1f - 2f * ((k * 0.618034f) % 1f) // up/down, between -1 and 1
    val ring = sqrt(1f - y * y)
    val around = k * 2.399963f // golden angle in radians
    return Position(cos(around) * ring * r, y * r, sin(around) * ring * r)
}

@Composable
fun OrbitDemo(
    startProtons: Int = 1,
    startNeutrons: Int = 1,
    startElectrons: Int = 1,
) {
    var protons by remember { mutableIntStateOf(startProtons) }
    var neutrons by remember { mutableIntStateOf(startNeutrons) }
    var electrons by remember { mutableIntStateOf(startElectrons) }

    // Clamp to pool size
    val protonCount = protons.coerceIn(0, MAX_PROTONS)
    val neutronCount = neutrons.coerceIn(0, MAX_NEUTRONS)
    val electronCount = electrons.coerceIn(0, MAX_ELECTRONS)

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    // Fixed pool of models: unused ones are hidden, so adding particles is instant
    val protonPool = List(MAX_PROTONS) { rememberModelInstance(modelLoader, SPHERE) }
    val neutronPool = List(MAX_NEUTRONS) { rememberModelInstance(modelLoader, SPHERE) }
    val electronPool = List(MAX_ELECTRONS) { rememberModelInstance(modelLoader, SPHERE) }

    // Color models once loaded
    protonPool.forEach { LaunchedEffect(it) { it?.setColor(PROTON_COLOR) } }
    neutronPool.forEach { LaunchedEffect(it) { it?.setColor(NEUTRON_COLOR) } }
    electronPool.forEach { LaunchedEffect(it) { it?.setColor(ELECTRON_COLOR) } }

    // Seconds since start, updated every frame
    var time by remember { mutableFloatStateOf(0f) }
    var startNanos by remember { mutableStateOf(0L) }
    var speedMultiplier by remember { mutableFloatStateOf(1f) }

    // Spread protons and neutrons evenly through the nucleus
    val totalNucleons = protonCount + neutronCount
    val protonSpots = mutableListOf<Int>()
    val neutronSpots = mutableListOf<Int>()
    for (k in 0 until totalNucleons) {
        val protonsSoFar = (k + 1) * protonCount / totalNucleons
        if (protonsSoFar > protonSpots.size) protonSpots += k else neutronSpots += k
    }

    // Spin nucleus around y
    val spin = time * 0.35f
    fun nucleonPosition(k: Int): Position {
        val p = nucleonOffset(k, totalNucleons)
        return Position(
            x = NUCLEUS_POSITION.x + p.x * cos(spin) - p.z * sin(spin),
            y = NUCLEUS_POSITION.y + p.y,
            z = NUCLEUS_POSITION.z + p.x * sin(spin) + p.z * cos(spin)
        )
    }

    // (subshell index, slot in ring, electrons in ring) for each electron
    val perSubshell = fillSubshells(electronCount)
    val electronSlots = perSubshell.flatMapIndexed { s, count ->
        List(count) { i -> Triple(s, i, count) }
    }

    fun electronPosition(slot: Triple<Int, Int, Int>): Position {
        val (s, i, count) = slot
        val shell = SUBSHELLS[s]
        val tilt = shell.tilt * (PI.toFloat() / 180f)
        // Evenly spaced around the ring
        val angle = time * shell.speed * speedMultiplier + i * (2f * PI.toFloat() / count)
        val flatX = cos(angle) * shell.radius
        val flatZ = sin(angle) * shell.radius
        // Tilt ring around x
        return Position(
            x = NUCLEUS_POSITION.x + flatX,
            y = NUCLEUS_POSITION.y + flatZ * sin(tilt),
            z = NUCLEUS_POSITION.z + flatZ * cos(tilt)
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFcc9bcc)) {
      Box(modifier = Modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            // Transparent so the Surface color shows through
            surfaceType = SurfaceType.TextureSurface,
            isOpaque = false,
            // Keep our positions, don't recenter
            autoCenterContent = false,
            onFrame = { frameTimeNanos ->
                if (startNanos == 0L) startNanos = frameTimeNanos
                time = (frameTimeNanos - startNanos) / 1_000_000_000f
            }
        ) {
            // Unused pool models stay hidden
            protonPool.forEachIndexed { i, instance ->
                instance?.let {
                    val shown = i < protonCount
                    ModelNode(
                        modelInstance = it,
                        scaleToUnits = NUCLEON_SIZE,
                        position = if (shown) nucleonPosition(protonSpots[i]) else NUCLEUS_POSITION,
                        isVisible = shown
                    )
                }
            }
            neutronPool.forEachIndexed { i, instance ->
                instance?.let {
                    val shown = i < neutronCount
                    ModelNode(
                        modelInstance = it,
                        scaleToUnits = NUCLEON_SIZE,
                        position = if (shown) nucleonPosition(neutronSpots[i]) else NUCLEUS_POSITION,
                        isVisible = shown
                    )
                }
            }

            electronPool.forEachIndexed { i, instance ->
                instance?.let {
                    val slot = electronSlots.getOrNull(i)
                    ModelNode(
                        modelInstance = it,
                        scaleToUnits = ELECTRON_SIZE,
                        position = if (slot != null) electronPosition(slot) else NUCLEUS_POSITION,
                        isVisible = slot != null,
                        // Tap to speed up orbits
                        apply = {
                            onSingleTapConfirmed = {
                                speedMultiplier += 0.5f
                                true
                            }
                        }
                    )
                }
            }
        }

        Text(
            text = "Add One Electron",
            color = Color(0xFFe8b88b),
            fontFamily = Nunito,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 25.dp, bottom = 25.dp)
                .clickable {
                    electrons++
                }
        )

          Text(
              text = "Add One Proton",
              fontFamily = Nunito,
              color = Color(0xFFe8b88b),
              modifier = Modifier
                  .align(Alignment.BottomStart)
                  .padding(start = 25.dp, bottom = 50.dp)
                  .clickable {
                      protons++
                  }
          )

          Text(
              text = "Add One Neutron",
              fontFamily = Nunito,
              color = Color(0xFFe8b88b),
              modifier = Modifier
                  .align(Alignment.BottomStart)
                  .padding(start = 25.dp, bottom = 75.dp)
                  .clickable {
                      if (neutrons < protons + 3) neutrons++
                  }
          )
        //Debug ---------------------------------------------------------------
          Text(
              "protons: $protons \nelectrons: $electrons \nneutrons: $neutrons",
              fontFamily = monospace
          )
      }
    }
}

package com.owntracks.auto.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Body height as a fraction of the available height, for each snap position. */
private val SNAP_FRACTIONS = floatArrayOf(0f, 0.35f, 0.62f)
private const val DEFAULT_SNAP = 1

/** Fling speed (px/s) above which a release moves to the next snap position. */
private const val FLING_VELOCITY = 1200f

/**
 * A bottom panel whose [body] can be resized by dragging its [header] up or
 * down. The header always stays visible; the body snaps to hidden, medium or
 * tall. Tapping the grab handle toggles between hidden and the last open size.
 *
 * @param availableHeight the space shared by the panel and whatever sits above it
 */
@Composable
fun ResizablePanel(
    availableHeight: Dp,
    header: @Composable ColumnScope.() -> Unit,
    body: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxPx = with(density) { availableHeight.toPx() }
    val anchors = SNAP_FRACTIONS.map { it * maxPx }

    // The snap position survives rotation and process death; the live height does not need to.
    var snapIndex by rememberSaveable { mutableIntStateOf(DEFAULT_SNAP) }
    var lastOpenIndex by rememberSaveable { mutableIntStateOf(DEFAULT_SNAP) }
    var bodyPx by remember { mutableFloatStateOf(anchors[snapIndex]) }
    val scope = rememberCoroutineScope()

    // Re-apply the snap position when the available height changes (e.g. rotation).
    LaunchedEffect(maxPx) { bodyPx = anchors[snapIndex] }

    fun settleTo(index: Int, velocity: Float = 0f) {
        snapIndex = index
        if (index > 0) lastOpenIndex = index
        scope.launch {
            animate(initialValue = bodyPx, targetValue = anchors[index], initialVelocity = velocity) { v, _ ->
                bodyPx = v
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        // Dragging down (positive delta) shrinks the body.
        bodyPx = (bodyPx - delta).coerceIn(0f, anchors.last())
    }

    val expanded = snapIndex > 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
    ) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            // Upward fling grows, downward fling shrinks; otherwise snap to nearest.
                            val nearest = anchors.indices.minBy { abs(anchors[it] - bodyPx) }
                            val target = when {
                                velocity < -FLING_VELOCITY ->
                                    anchors.indices.firstOrNull { anchors[it] > bodyPx + 1f } ?: anchors.lastIndex
                                velocity > FLING_VELOCITY ->
                                    anchors.indices.lastOrNull { anchors[it] < bodyPx - 1f } ?: 0
                                else -> nearest
                            }
                            settleTo(target, -velocity)
                        },
                    ),
            ) {
                // Grab handle: a visual cue for dragging, and a tap target for keyboard/TalkBack users.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { settleTo(if (expanded) 0 else lastOpenIndex) }
                        .semantics {
                            contentDescription = "Device panel"
                            stateDescription = if (expanded) "Expanded, tap to collapse" else "Collapsed, tap to expand"
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
                header()
            }
            body(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { bodyPx.toDp() })
                    .clipToBounds(),
            )
        }
    }
}

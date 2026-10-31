package com.strideflow.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strideflow.app.model.GpsPoint
import com.strideflow.app.ui.theme.*
import kotlin.math.*

@Composable
fun RouteMapView(
    points: List<GpsPoint>,
    modifier: Modifier = Modifier,
    isLiveTracking: Boolean = false
) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBackground)
            .border(1.dp, DarkOutline, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5.0f)
                    panOffset += pan
                }
            }
    ) {
        if (points.size < 2) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(NeonGreen)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isLiveTracking) "GPS Acquired · Start moving to render route" else "No GPS track recorded",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Find geographic bounding box
                var minLat = Double.MAX_VALUE
                var maxLat = -Double.MAX_VALUE
                var minLon = Double.MAX_VALUE
                var maxLon = -Double.MAX_VALUE

                for (p in points) {
                    if (p.latitude < minLat) minLat = p.latitude
                    if (p.latitude > maxLat) maxLat = p.latitude
                    if (p.longitude < minLon) minLon = p.longitude
                    if (p.longitude > maxLon) maxLon = p.longitude
                }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
                val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)

                val padding = 45f
                val drawWidth = canvasWidth - (padding * 2)
                val drawHeight = canvasHeight - (padding * 2)

                // Scaling factor to fit map in view
                val scale = min(drawWidth / lonSpan, drawHeight / latSpan).toFloat() * zoomScale

                val centerLon = (minLon + maxLon) / 2.0
                val centerLat = (minLat + maxLat) / 2.0

                fun project(lat: Double, lon: Double): Offset {
                    val x = (canvasWidth / 2f) + ((lon - centerLon) * scale).toFloat() + panOffset.x
                    // Invert latitude for screen coordinates (north is up)
                    val y = (canvasHeight / 2f) - ((lat - centerLat) * scale).toFloat() + panOffset.y
                    return Offset(x, y)
                }

                // Draw subtle grid lines
                val gridSpacing = 60f
                var gx = 0f
                while (gx < canvasWidth) {
                    drawLine(
                        color = DarkOutline.copy(alpha = 0.35f),
                        start = Offset(gx, 0f),
                        end = Offset(gx, canvasHeight),
                        strokeWidth = 1f
                    )
                    gx += gridSpacing
                }
                var gy = 0f
                while (gy < canvasHeight) {
                    drawLine(
                        color = DarkOutline.copy(alpha = 0.35f),
                        start = Offset(0f, gy),
                        end = Offset(canvasWidth, gy),
                        strokeWidth = 1f
                    )
                    gy += gridSpacing
                }

                // Draw route polyline
                val path = Path()
                points.forEachIndexed { index, p ->
                    val pt = project(p.latitude, p.longitude)
                    if (index == 0) {
                        path.moveTo(pt.x, pt.y)
                    } else {
                        path.lineTo(pt.x, pt.y)
                    }
                }

                // Path glow
                drawPath(
                    path = path,
                    color = BrightCyan.copy(alpha = 0.25f),
                    style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Core Path
                drawPath(
                    path = path,
                    color = BrightCyan,
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw Start Point Marker
                val startPt = project(points.first().latitude, points.first().longitude)
                drawCircle(color = NeonGreen, radius = 9f, center = startPt)
                drawCircle(color = Color.Black, radius = 4f, center = startPt)

                // Draw Current/End Point Marker
                val lastPt = project(points.last().latitude, points.last().longitude)
                drawCircle(color = VividRed.copy(alpha = 0.3f), radius = 16f, center = lastPt)
                drawCircle(color = VividRed, radius = 9f, center = lastPt)
                drawCircle(color = Color.White, radius = 4f, center = lastPt)
            }
        }

        // Overlay: Reset Center Button & Scale
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    zoomScale = 1.0f
                    panOffset = Offset.Zero
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated.copy(alpha = 0.9f))
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center Map", tint = BrightCyan, modifier = Modifier.size(18.dp))
            }
        }

        // Overlay: Offline Map Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface.copy(alpha = 0.85f))
                .border(0.5.dp, DarkOutline, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "OFFLINE GPS VECTOR MAP",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = BrightCyan
            )
        }
    }
}

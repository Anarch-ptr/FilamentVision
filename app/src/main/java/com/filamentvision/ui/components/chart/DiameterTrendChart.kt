package com.filamentvision.ui.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.filamentvision.data.repository.PersistedAlarmEvent
import kotlin.math.abs

@Composable
fun DiameterTrendChart(
    points: List<TrendChartPoint>,
    alarms: List<PersistedAlarmEvent>,
    targetDiameter: Double,
    showCameraA: Boolean,
    showCameraB: Boolean,
    showFused: Boolean,
    inspectedPoint: TrendChartPoint?,
    onInspect: (TrendChartPoint?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .pointerInput(points) {
                detectTapGestures { tap ->
                    if (points.isEmpty()) return@detectTapGestures
                    val first = points.first().timestamp
                    val duration = (points.last().timestamp - first).coerceAtLeast(1L)
                    val selectedTime = first + (tap.x / size.width * duration).toLong()
                    onInspect(points.minByOrNull { abs(it.timestamp - selectedTime) })
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val warning = targetDiameter * 0.02
        val critical = targetDiameter * 0.05
        val values = buildList {
            points.forEach { point ->
                add(point.fusedDiameter)
                point.diameterA?.let(::add)
                point.diameterB?.let(::add)
            }
            add((targetDiameter - critical).toFloat())
            add((targetDiameter + critical).toFloat())
        }
        val minY = values.min().toDouble()
        val maxY = values.max().toDouble()
        val rangeY = (maxY - minY).coerceAtLeast(0.001)
        val firstTime = points.first().timestamp
        val timeRange = (points.last().timestamp - firstTime).coerceAtLeast(1L)
        fun x(timestamp: Long) = (timestamp - firstTime).toFloat() / timeRange * size.width
        fun y(value: Double) = size.height - ((value - minY) / rangeY * size.height).toFloat()

        drawLine(Color(0xFFB00020), Offset(0f, y(targetDiameter - critical)), Offset(size.width, y(targetDiameter - critical)), 2f)
        drawLine(Color(0xFFB00020), Offset(0f, y(targetDiameter + critical)), Offset(size.width, y(targetDiameter + critical)), 2f)
        drawLine(Color(0xFFF59E0B), Offset(0f, y(targetDiameter - warning)), Offset(size.width, y(targetDiameter - warning)), 1.5f)
        drawLine(Color(0xFFF59E0B), Offset(0f, y(targetDiameter + warning)), Offset(size.width, y(targetDiameter + warning)), 1.5f)
        drawLine(Color(0xFF64748B), Offset(0f, y(targetDiameter)), Offset(size.width, y(targetDiameter)), 1f)

        fun drawSeries(color: Color, value: (TrendChartPoint) -> Float?) {
            val path = Path()
            var started = false
            points.forEach { point ->
                val diameter = value(point) ?: return@forEach
                if (!started) {
                    path.moveTo(x(point.timestamp), y(diameter.toDouble()))
                    started = true
                } else {
                    path.lineTo(x(point.timestamp), y(diameter.toDouble()))
                }
            }
            if (started) drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
        if (showCameraA) drawSeries(Color(0xFF2563EB), TrendChartPoint::diameterA)
        if (showCameraB) drawSeries(Color(0xFF9333EA), TrendChartPoint::diameterB)
        if (showFused) drawSeries(Color(0xFF059669)) { it.fusedDiameter }

        alarms.forEach { alarm ->
            if (alarm.timestamp in firstTime..points.last().timestamp) {
                val alarmX = x(alarm.timestamp)
                drawLine(Color(0xFFDC2626), Offset(alarmX, 0f), Offset(alarmX, size.height), 2f)
            }
        }
        inspectedPoint?.let { selected ->
            val selectedX = x(selected.timestamp)
            drawLine(Color.DarkGray, Offset(selectedX, 0f), Offset(selectedX, size.height), 2f)
            drawCircle(Color.White, radius = 7f, center = Offset(selectedX, y(selected.fusedDiameter.toDouble())))
            drawCircle(Color(0xFF059669), radius = 5f, center = Offset(selectedX, y(selected.fusedDiameter.toDouble())))
        }
    }
}

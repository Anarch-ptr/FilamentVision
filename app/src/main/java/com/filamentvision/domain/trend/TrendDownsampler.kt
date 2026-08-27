package com.filamentvision.domain.trend

import com.filamentvision.ui.components.chart.TrendChartPoint
import kotlin.math.ceil

/** Retains per-bucket extrema instead of averaging away short spikes. */
object TrendDownsampler {
    fun downsample(
        points: List<TrendChartPoint>,
        maxPoints: Int,
    ): List<TrendChartPoint> {
        require(maxPoints >= MAX_CANDIDATES_PER_BUCKET)
        if (points.size <= maxPoints) return points

        val bucketCount = maxPoints / MAX_CANDIDATES_PER_BUCKET
        val bucketSize = ceil(points.size.toDouble() / bucketCount).toInt()
        return buildList(maxPoints) {
            var bucketStart = 0
            while (bucketStart < points.size) {
                val bucketEnd = minOf(bucketStart + bucketSize, points.size)
                addAll(selectBucketCandidates(points.subList(bucketStart, bucketEnd)))
                bucketStart = bucketEnd
            }
        }.take(maxPoints)
    }

    private fun selectBucketCandidates(bucket: List<TrendChartPoint>): List<TrendChartPoint> {
        val candidates = ArrayList<TrendChartPoint>(MAX_CANDIDATES_PER_BUCKET)
        candidates += bucket.first()
        candidates += bucket.last()
        candidates += bucket.minBy(TrendChartPoint::fusedDiameter)
        candidates += bucket.maxBy(TrendChartPoint::fusedDiameter)
        bucket.filter { it.diameterA != null }.minByOrNull { it.diameterA!! }?.let(candidates::add)
        bucket.filter { it.diameterA != null }.maxByOrNull { it.diameterA!! }?.let(candidates::add)
        bucket.filter { it.diameterB != null }.minByOrNull { it.diameterB!! }?.let(candidates::add)
        bucket.filter { it.diameterB != null }.maxByOrNull { it.diameterB!! }?.let(candidates::add)
        return candidates.distinctBy(TrendChartPoint::timestamp).sortedBy(TrendChartPoint::timestamp)
    }

    private const val MAX_CANDIDATES_PER_BUCKET = 8
}

package com.example.quic_ar_client.screens

import io.github.sceneview.node.ModelNode

data class LODLevel(
    val level: Long,
    val geometricError: Float,
    val fileSize: Long,
    var estimateDownloadingTime: Float,
    var sse: Float
)
data class ObjectInfo(
    var distance: Float = 0f,
    var priority: Long = 0,
    var indexOfChildNodes: Int = -1,
    var currentLOD: Int = 1,
    var lodLevelGroup: List<LODLevel> = lodLevelGroup1,
    var modelNode: ModelNode? = null
)

val lodLevelGroup1: List<LODLevel> = listOf(
    LODLevel(1, 0.029791176.toFloat(), 411924, 0.19438033f, 100f),
    LODLevel(2, 0.029098812.toFloat(), 596667, 0.2815576f, 100f),
    LODLevel(3, 0.02444897.toFloat(), 679431, 0.32061258f, 100f),
    LODLevel(4, 0.021623002.toFloat(), 810875, 0.38263893f, 100f),
    LODLevel(5, 0.017669708.toFloat(), 915269, 0.43190074f, 100f),
    LODLevel(6, 0.013755348.toFloat(), 1077972, 0.50867766f, 100f),
    LODLevel(7, 0f, 3122008, 1.4732255f, 100f)
)

val lodLevelGroup2: List<LODLevel> = listOf(
    LODLevel(1, 0.989185619.toFloat(), 78197, 0f, 100f),
    LODLevel(2, 0.981951954.toFloat(), 126957, 0f, 100f),
    LODLevel(3, 0.974212655.toFloat(), 169142, 0f, 100f),
    LODLevel(4, 0.948414992.toFloat(), 316330, 0f, 100f),
    LODLevel(5, 0.871011681.toFloat(), 688620, 0f, 100f),
    LODLevel(6,0.57000454.toFloat(), 1694699, 0f, 100f),
    LODLevel(7, 0f, 3464545, 0f, 100f)
)







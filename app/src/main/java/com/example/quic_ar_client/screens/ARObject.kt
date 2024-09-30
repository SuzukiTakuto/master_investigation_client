package com.example.quic_ar_client.screens

data class LODLevel(
    val level: Long,
    val geometricError: Float,
    val fileSize: Long,
    var estimateDownloadingTime: Float
)
data class ObjectInfo(
    var distance: Float = 0f,
    var priority: Long = 8,
    var indexOfChildNodes: Int = 0,
    var currentLOD: Int = 1,
    var lodLevelGroup: List<LODLevel> = lodLevelGroup1
)

val lodLevelGroup1: List<LODLevel> = listOf(
    LODLevel(1, 0.029544084.toFloat(), 462823, 0f),
    LODLevel(2, 0.029404638.toFloat(), 601800, 0f),
    LODLevel(3, 0.026479703.toFloat(), 683098, 0f),
    LODLevel(4, 0.022068396.toFloat(), 814213, 0f),
    LODLevel(5, 0.01978754.toFloat(), 915269, 0f),
    LODLevel(6, 0.017598001.toFloat(), 1077972, 0f),
    LODLevel(7, 0f, 3122008, 0f)
)

val lodLevelGroup2: List<LODLevel> = listOf(
    LODLevel(1, 0.989185619.toFloat(), 78197, 0f),
    LODLevel(2, 0.981951954.toFloat(), 126957, 0f),
    LODLevel(3, 0.974212655.toFloat(), 169142, 0f),
    LODLevel(4, 0.948414992.toFloat(), 316330, 0f),
    LODLevel(5, 0.871011681.toFloat(), 688620, 0f),
    LODLevel(6,0.57000454.toFloat(), 1694699, 0f),
    LODLevel(7, 0f, 3464545, 0f)
)







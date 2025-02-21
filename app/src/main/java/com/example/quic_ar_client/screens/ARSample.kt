package com.example.quic_ar_client.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quic_ar_client.ui.theme.Quic_ar_clientTheme
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingFailureReason
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import android.util.Log
import android.view.Display.Mode
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import com.google.ar.core.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import quic.Quic
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.lang.Exception
import java.nio.Buffer
import java.nio.ByteBuffer
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.romainguy.kotlin.math.PI
import dev.romainguy.kotlin.math.all
import dev.romainguy.kotlin.math.x
import io.github.sceneview.node.Node
import kotlin.math.sqrt
import java.util.*
import java.util.Timer
import kotlin.concurrent.timerTask
import io.github.sceneview.node.SphereNode
import io.github.sceneview.collision.Vector3
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.RenderableNode
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.tan
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// distance: オブジェクトとユーザの距離。priority: 現在の優先度(想定外の優先度で初期化)。indexOfChildNodes: childNodesのどこにそのオブジェクトが格納されているか。

private const val kMaxModelInstances = 10
private const val numberOfObject = 1

// カメラの視野角を定義（水平72.39279度、垂直57.59845度）
private const val HORIZONTAL_FOV = 75f
private const val HORIZONTAL_FOV_RAD = HORIZONTAL_FOV * PI / 180
private const val VERTICAL_FOV = 67f

// スクリーンサイズ
private const val screenWidth = 2560
private const val screenHeight = 1600

// 実験のアルゴリズム(2: 予測地点の周りを持ってくる、3: 視線予測のみでキャンセル無し, 4: キャンセルあり)
private const val experimental_type = 4

// ターゲットSSE
private const val targetSSE = 14f

val mutex = Mutex()

@Composable
fun ARSample() {
    val objectInfoList by remember { mutableStateOf(mutableMapOf<String, ObjectInfo>()) }
    val objectInfoListForMeasure by remember { mutableStateOf(mutableMapOf<String, ObjectInfo>()) }

    val previousVisibilityMap by remember {
        mutableStateOf(HashMap<String, Boolean>(66).also { map ->
            for (i in 1..66) {
                map["marker$i"] = false
            }
        })
    }

    val insightTimeMap by remember {
        mutableStateOf(HashMap<String, Long>(66).also { map ->
            for (i in 1..66) {
                map["marker$i"] = 0
            }
        })
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        data class FetchObjectInfo(
            val index: Int,
            val level : Long,
            val value: Float,
            val LODLevel: LODLevel
        )

        var cacheTotal by remember { mutableStateOf<Int>(0) }
        var cacheFailedTotal by remember { mutableStateOf<Int>(0) }
        var cacheShowMiss by remember { mutableStateOf<Int>(0) }
        var totalFrame by remember { mutableStateOf<Int>(0) }
        var totalLODMissFrame by remember { mutableStateOf<Int>(0) }
        var totalShowMissFrame by remember { mutableStateOf<Int>(0) }

        var cancel70Count by remember { mutableStateOf<Int>(0) }
        var noCancel70Count by remember { mutableStateOf<Int>(0) }
        var updateLODCancel by remember { mutableStateOf<Int>(0) }

        var startTime by remember { mutableStateOf<Long>(0) }
        var endTime by remember { mutableStateOf<Long>(0) }

        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine) // モデル本体のロード
        val materialLoader = rememberMaterialLoader(engine) // オブジェクトの外観のロード
        val cameraNode = rememberARCameraNode(engine)
        val childNodes = rememberNodes() // シーンのノードのリスト
        val view = rememberView(engine)
        val collisionSystem = rememberCollisionSystem(view) // ノード間の衝突を扱う物理システム、ノードのヒットテスト

        var planeRenderer by remember { mutableStateOf(true) } // 平面検出するかどうか？

        var centerPose by remember {mutableStateOf<Pose?>(null)}
        var globalCameraPose by remember {mutableStateOf<Pose?>(null)}
        var session by remember { mutableStateOf<Session?>(null) }

        var trackingFailureReason by remember {
            mutableStateOf<TrackingFailureReason?>(null) // ARトラッキングの失敗についての情報を格納。 ex. BAD_STATE, CAMERA_UNAVAILABLE, EXCESSIVE_MOTION
        }

        var planeDetected by remember { mutableStateOf(false) }

        var poses by remember {mutableStateOf(emptyList<Pose>())}

        var lastAngle by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
        var currentPosition by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var predictedPosition by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var predictedCameraPosition by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var predictedCameraRotation by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var currentCameraRotation by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var lastPredictedPosition by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var lastPosition by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
        var lastRotation by remember { mutableStateOf(Triple(0f, 0f, 0f)) }

        var predictedNodeIndex1 by remember {mutableStateOf<Int>(0)}
        var predictedNodeIndex2 by remember {mutableStateOf<Int>(0)}

        var plane by remember { mutableStateOf<Plane?>(null) }

        var nowDownloadingLods by remember { mutableStateOf(mutableMapOf<String, FetchObjectInfo>()) }
        var cacheObject by remember { mutableStateOf(mutableMapOf<String, Buffer>()) }

        var downloadingTime by remember { mutableStateOf<Float>(0f) }

        data class NowAndNextLevel(
            var now: Long,
            var next: Long
        )
        var objectNowLevelAndNextLevel by remember { mutableStateOf(mutableMapOf<Int, NowAndNextLevel>()) }

        // オブジェクトのフェッチ処理
        // ===============================================================================
        // ===============================================================================
        // ===============================================================================
        // ===============================================================================
        var fetchCount = 0
        suspend fun fetchObject(name: String, LODLevel: Long): Boolean {
            fetchCount ++
//            Log.d("fetchCounter", "${fetchCount}回目のフェッチ")
            val startTime = System.currentTimeMillis()
            Log.d("表示時間計測", "${name} リクエスト 優先度${LODLevel}でリクエスト ${System.currentTimeMillis()}")
            Log.d("表示orLOD遅延", "${name} リクエスト 優先度${LODLevel}でリクエスト ${System.currentTimeMillis()}")
            val result = withContext(Dispatchers.IO) { Quic.fetch(name, LODLevel) }
            Log.d("表示orLOD遅延", "${name} ダウンロード完了 優先度${LODLevel}のダウンロード完了 ${System.currentTimeMillis()}")
            val endTime = System.currentTimeMillis() // 処理終了時間を取得


//            var result = withContext(Dispatchers.IO) { Quic.httP2ArFetch(name, LODLevel) }
//            Log.d("cancelTest", "${result.isComplete}")
//            Log.d("cancelTest", "${name}: fetchObject内でgo呼び出し終了")

            var byteArray = result.receiveData
            downloadingTime = result.downloadingTime.toFloat()
//            calculateDownloadingTimeOfLOD(objectInfoList[name]!!.lodLevelGroup[LODLevel.toInt() - 1].fileSize, downloadingTime, objectInfoList[name]!!)

//            val startTime = System.currentTimeMillis()
//            var byteArray = Quic.httP2Fetch(name, "test")
//            val endTime = System.currentTimeMillis() // 処理終了時間を取得
            val elapsedTime = endTime - startTime // 処理時間を計算
//            println("cancelTest: $name の処理時間 = $elapsedTime ms. データサイズ = ${byteArray.size}")

            if (result.isComplete){
//            if (true){
                val buffer = ByteBuffer.wrap(byteArray)

                cacheObject[name] = buffer
//                Log.d("cancelTest", "${result.receiveData.size}")
                val elapsedTime = endTime - startTime // 処理時間を計算
//                Log.d("表示orLOD遅延", "$name の処理時間 = $elapsedTime ms")

                nowDownloadingLods.remove(name) // 現在のダウンロードリストから削除
//                Log.d(
//                    "cancelTest",
//                    "${name}をキャッシュに格納&ダウンロードリストから削除: $cacheObject $nowDownloadingLods"
//                )
            }

            return result.isComplete
        }

        fun createAnchorNode(
            engine: Engine,
            modelLoader: ModelLoader,
            materialLoader: MaterialLoader,
            modelInstances: MutableList<ModelInstance>,
            anchor: Anchor,
            buffer: Buffer?,
            pose:  Pose
        ): Pair<AnchorNode, ModelNode> {
            try{
                val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                val modelNode = ModelNode(
                    modelInstance = modelInstances.apply {
                        if (isEmpty()) {
                            try {
                                this += modelLoader.createInstancedModel(
                                    buffer = buffer!!,
                                    count = kMaxModelInstances
                                )
                            } catch (e: Exception) {

                                println("Error creating instanced model: ${e.message}")
                            }
                        }
                    }.removeLast(),
                    // Scale to fit in a 0.5 meters cube
                    scaleToUnits = 0.5f
                ).apply {
                    // Model Node needs to be editable for independent rotation from the anchor rotation
                    isEditable = true
                }
                (modelNode.childNodes.firstOrNull() as? RenderableNode)?.position = Position(0f, 0f, 0f)
                (modelNode.childNodes.firstOrNull() as? RenderableNode)?.rotation = Rotation(180f, 90f, 90f)
                anchorNode.addChildNode(modelNode)


                return Pair(anchorNode, modelNode)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        fun addNodes(
            childNodes: SnapshotStateList<Node>,
            modelInstance: MutableList<ModelInstance>,
            anchor: Anchor,
            buffer: Buffer,
            engine: Engine,
            modelLoader: ModelLoader,
            materialLoader: MaterialLoader,
            name: String,
            pose:  Pose,
            poseIndex: Int,
        ) {
            try{
                var index = objectInfoList[name]?.indexOfChildNodes
                val (node, modelNode) =
                    createAnchorNode(engine, modelLoader, materialLoader, modelInstance, anchor, buffer, pose)
                val currentPose = poses[poseIndex]

                // 実際に表示された座標にposesを変更
                poses = poses.toMutableList().also { list ->
                    if (poseIndex in list.indices) {
                        list[poseIndex] = Pose(floatArrayOf(node.worldPosition.x, node.worldPosition.y, node.worldPosition.z), floatArrayOf(currentPose.qx(), currentPose.qy(), currentPose.qz(), currentPose.qw()))
                    }
                }

                if (objectInfoList[name]?.indexOfChildNodes == -1) { // 最初のフェッチの時
                    childNodes += node
                    objectInfoList[name]?.indexOfChildNodes = childNodes.size - 1
                    objectInfoList[name]?.modelNode = modelNode
                } else { // 更新のフェッチの時
                    objectInfoList[name]?.modelNode = modelNode
                    childNodes[index!!].destroy()
                    childNodes[index!!] = node
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun createAnchor(pose: Pose, session: Session): Anchor {
            try{
//                val anchor = plane?.createAnchorOrNull(pose)
                val anchor = session.createAnchor(pose)
                return anchor!!
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        fun displayObject(
            name: String,
            childNodes: SnapshotStateList<Node>,
            engine: Engine,
            modelLoader: ModelLoader,
            materialLoader: MaterialLoader,
            poseIndex: Int,
            pose:  Pose?,
            session: Session?,
            buffer: Buffer
        ) {
            try {
                buffer?.let {
                    val anchor = createAnchor(pose!!, session!!)
                    val modelInstance = mutableListOf<ModelInstance>()
                    addNodes(childNodes, modelInstance, anchor, it, engine, modelLoader, materialLoader, name, pose!!, poseIndex)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // ===============================================================================
        // ===============================================================================
        // ===============================================================================
        // ===============================================================================

        // SSEの計算
        fun calculateSSE(distance: Float, geometricError: Float): Float {
            return (geometricError * screenWidth) / (2 * distance * (tan(HORIZONTAL_FOV_RAD / 2)))
        }

        // LODの品質貢献度の計算
        fun calculateQuality(sse: Float): Float {
            if (sse >= targetSSE) {
                return 1.0f / (1.0f + (sse - targetSSE))
            } else {
                return 1f
            }
        }

        // LODユーティリティの計算
        fun calculateUtility(objectInfo: ObjectInfo, selectedLOD: Long): Float {
            val lod = objectInfo.lodLevelGroup[selectedLOD.toInt() - 1]
            //val sse = calculateSSE(objectInfo.distance, lod.geometricError)
            val quality = calculateQuality(objectInfo.lodLevelGroup[selectedLOD.toInt() - 1].sse)

            val downloadTime = lod.estimateDownloadingTime
            val currentLOD = objectInfo.currentLOD
//            Log.d("allLODList", "${objectInfo.lodLevelGroup[currentLOD - 1].sse},,,,${objectInfo.lodLevelGroup[selectedLOD.toInt() - 1].sse}")
            if (objectInfo.lodLevelGroup[currentLOD - 1].sse <= targetSSE || objectInfo.lodLevelGroup[currentLOD - 1].sse <= objectInfo.lodLevelGroup[selectedLOD.toInt() - 1].sse) {
                return 0f
            }
            return quality / (1f + downloadTime)
        }


        data class PredictedObjectInfo(
            val markerString: String,
            val pose: Pose,
            val priority: Long
        )

        // オブジェクトが視野内にあるかどうかを判断する関数
        fun isInFieldOfView(objectPose: Pose, now: Boolean): Boolean {
            val cameraPose = globalCameraPose ?: return false

            // オブジェクトの位置
            val objectPoseArray = floatArrayOf(
                objectPose.tx(),
                objectPose.ty(),
                objectPose.tz()
            )
            // 予測位置の2m前方
            val predictedPositionArray = floatArrayOf(
                predictedPosition.first,
                predictedPosition.second,
                predictedPosition.third
            )
            // ユーザの予測位置
            val predictedCameraPoseArray = floatArrayOf(
                predictedCameraPosition.first,
                predictedCameraPosition.second,
                predictedCameraPosition.third
            )
            // ユーザの位置
            var cameraPoseArray = floatArrayOf(
                cameraPose.tx(),
                cameraPose.ty(),
                cameraPose.tz()
            )
            // 現在の2m前方
            var currentPoseArray = floatArrayOf(
                currentPosition.first,
                currentPosition.second,
                currentPosition.third
            )

            // nowがtrueなら、今の視点での判定がしたいからobjectPose - cameraPose, そうでないなら予測位置での判定だからobjectPose - predictedCameraPose
            val cameraToObject = floatArrayOf(
                if (now) objectPoseArray[0] - cameraPoseArray[0] else objectPoseArray[0] - predictedCameraPoseArray[0],
                if (now) objectPoseArray[1] - cameraPoseArray[1] else objectPoseArray[1] - predictedCameraPoseArray[1],
                if (now) objectPoseArray[2] - cameraPoseArray[2] else objectPoseArray[2] - predictedCameraPoseArray[2]
            )

            // カメラの前方ベクトルを取得
            val cameraForward = floatArrayOf(
                if (now) currentPoseArray[0] - cameraPoseArray[0] else  predictedPositionArray[0] - predictedCameraPoseArray[0],
                if (now) currentPoseArray[1] - cameraPoseArray[1] else  predictedPositionArray[1] - predictedCameraPoseArray[1],
                if (now) currentPoseArray[2] - cameraPoseArray[2] else  predictedPositionArray[2] - predictedCameraPoseArray[2]
            )

            // x-z平面（水平面）での計算
            val cameraToObjectHorizontal = floatArrayOf(cameraToObject[0], cameraToObject[2])
            val cameraForwardHorizontal = floatArrayOf(cameraForward[0], cameraForward[2])

            val dotProductHorizontal = cameraToObjectHorizontal[0] * cameraForwardHorizontal[0] +
                    cameraToObjectHorizontal[1] * cameraForwardHorizontal[1]
            val magnitudeProductHorizontal = sqrt((cameraToObjectHorizontal[0] * cameraToObjectHorizontal[0]) + (cameraToObjectHorizontal[1] * cameraToObjectHorizontal[1])) *
                    sqrt((cameraForwardHorizontal[0] * cameraForwardHorizontal[0]) + (cameraForwardHorizontal[1] * cameraForwardHorizontal[1]))

            val horizontalAngle = Math.toDegrees(acos(dotProductHorizontal / magnitudeProductHorizontal).toDouble())

            val arctanCameraToForward = atan2(cameraForward[1].toDouble(), sqrt((cameraForwardHorizontal[0] * cameraForwardHorizontal[0]) + (cameraForwardHorizontal[1] * cameraForwardHorizontal[1])).toDouble())
            val arctanCameraToObject = atan2(cameraToObject[1].toDouble(), sqrt((cameraToObjectHorizontal[0] * cameraToObjectHorizontal[0]) + (cameraToObjectHorizontal[1] * cameraToObjectHorizontal[1])).toDouble())
            val verticalAngle = abs((arctanCameraToForward - arctanCameraToObject) * 100)

            // 視野角の半分と比較
            return horizontalAngle <= HORIZONTAL_FOV / 2 && verticalAngle <= VERTICAL_FOV / 2
        }

        // オブジェクトの優先度を取得
        fun getPriorityOfObject(markerString: String, distance: Float, objectPose: Pose): Long {
            if (experimental_type != 2) {
                if (!isInFieldOfView(objectPose, false)) {
                    return 0 // 視野外のオブジェクトは優先度0（フェッチしない）
                }
            }
//            Log.d("distanceee", distance.toString())

            if (distance < 1.2) {
                val priority = 7L
                return priority
            } else if (distance < 2.0) {
                val priority = 6L
                return priority
            } else if (distance < 2.8) {
                val priority = 5L
                return priority
            } else if (distance < 3.6) {
                val priority = 4L
                return priority
            } else if (distance < 4.4) {
                val priority = 3L
                return priority
            } else if (distance < 5.2) {
                val priority = 2L
                return priority
            } else {
                val priority = 1L
                return priority
            }
        }

        fun cancelStream(marker: String) {
//            Log.d("cancelTest", "${marker}: キャンセル関数")
            nowDownloadingLods.remove(marker) // 現在のダウンロードリストから削除
            Quic.cancelStream(marker) // ストリームをキャンセル
//            Log.d("cancelTest", "${marker}: 視野外だからストリーム削除。 $cacheObject $nowDownloadingLods")
        }

        fun httpCancelStream(marker: String) {
            Quic.httP2CancelStream(marker)
        }

        // 1秒ごとに直近のダウンロードサイズを格納
        var downloadSizeFor5seconds = ArrayDeque<Long>(5)
        val estimateTimer = Timer()
        val estimateTask = timerTask {
            val size = Quic.getTotalAtThatTime()
            downloadSizeFor5seconds.addLast(size)
            if (downloadSizeFor5seconds.size > 5) {
                downloadSizeFor5seconds.removeFirst()
            }
        }
        val estimateDelay = 0L
        val estimateLong = 1000L

        fun getMovingAverage(): Float {
            var total = 0f
            downloadSizeFor5seconds.forEach { size ->
                total += size
            }

            return total / 5
        }

        // オブジェクトグループの各LODのダウンロード時間を推定
        fun calculateDownloadingTimeOfLOD(movingAverage: Float, objectInfo: ObjectInfo) {
            objectInfo.lodLevelGroup.forEach {
                if (movingAverage == 0f) {
                    it.estimateDownloadingTime = 0f
                } else {
                    it.estimateDownloadingTime = it.fileSize / movingAverage
                }
            }
        }

        var count = 0
        // フェッチするオブジェクトLODの決定
        fun lodSelection(updateObjectList: List<Int>) {
            Log.d("選択間隔", "${System.currentTimeMillis()}")
            Log.d("allLODList", "==========================================================================================================")
            Log.d("allLODList", "update List: ${updateObjectList}")
            count ++
//            Log.d("allLODList", "${count}回目のlodselectionスタート")
            var movingAverage = getMovingAverage() // 直近5秒の移動平均を取得
            var allLODList = mutableListOf<FetchObjectInfo>() // オブジェクトIDとpriority * utilityのマップ
            updateObjectList.forEachIndexed { index, id ->
                val key = "marker${id + 1}"
                val distance = objectInfoList[key]?.distance
                calculateDownloadingTimeOfLOD(movingAverage, objectInfoList[key]!!) // ダウンロード時間の推定

                val priority = objectInfoList[key]?.priority // そのオブジェクトの優先度を取得
                Log.d("allLODList", "${key}'s priority is ${priority}, currentLOD = ${objectInfoList[key]!!.currentLOD}")

                if (priority != 0L) {
                    if (objectInfoList[key]!!.currentLOD != 7) {
                        for (i in 0..6) { // 各LODのSSEを計算
                            objectInfoList[key]!!.lodLevelGroup[i].sse = calculateSSE(objectInfoList[key]!!.distance, objectInfoList[key]!!.lodLevelGroup[i].geometricError)
                        }
                        for (i in objectInfoList[key]!!.currentLOD..7) {  // priority * utilityを計算
                            val value = calculateUtility(objectInfoList[key]!!, i.toLong()) * priority!!
                            val newFetchObjectInfo = FetchObjectInfo(
                                index,
                                i.toLong(),
                                value,
                                objectInfoList[key]!!.lodLevelGroup[i - 1]
                            )
                            allLODList.add(newFetchObjectInfo)
                        }
                    }
                }
                else { // 視野外&ダウンロード中ならストリームのキャンセルとダウンロード中リストからの削除
                    if (nowDownloadingLods.containsKey(key)) {
                        // 70%ダウンロード完了してるならキャンセルしない
                        if (!Quic.isDownloadProgressReached(key, 70L, nowDownloadingLods[key]?.LODLevel?.level!!)) {
//                            Log.d("cancelTest", "${key}: 視野外キャンセル対象")
                            if (experimental_type == 4) {
                                cancelStream(key)
                                Log.d("allLODList", "${key}のLOD比較: ${objectInfoList["marker${index + 1}"]!!.currentLOD},,,,,${objectInfoList["marker${index + 1}"]!!.prevLOD}")
                                objectInfoList["marker${index + 1}"]!!.currentLOD = objectInfoList["marker${index + 1}"]!!.prevLOD
//                            httpCancelStream(key)
                                cancel70Count ++
                                Log.d("表示orLOD遅延", "${key} 70%キャンセル回数 = ${cancel70Count} ${System.currentTimeMillis()}")
                            }
                        } else {
                            noCancel70Count ++
                            Log.d("表示orLOD遅延", "${key} 70%以上非キャンセル回数 = ${noCancel70Count} ${System.currentTimeMillis()}")
                        }
                    }
                }
            }

            // priority * utilityでソート
            val sortedList = allLODList.sortedByDescending { it.value }

            val objectsProcessed = mutableSetOf<Int>()
            var scheduledFetchLOD = mutableListOf<FetchObjectInfo>()
            sortedList.forEach {
//                Log.d("allLODList", "${it}")
                if (it.index in objectsProcessed) return@forEach // もうそのオブジェクトについては選択されてる場合スキップ
                if (objectInfoList["marker${it.index + 1}"]!!.currentLOD >= it.LODLevel.level.toInt()) return@forEach // 現在のLODレベルの方が高い場合スキップ
                // キャッシュに存在するならここでスキップ
                if (it.value == 0f) return@forEach // valueが0の場合、それ以上今は上げる必要がないからスキップ
                if (nowDownloadingLods.containsKey("marker${it.index + 1}")) {
                    if (nowDownloadingLods["marker${it.index + 1}"]?.level!! < it.level) {
                        cancelStream("marker${it.index + 1}")
                        updateLODCancel ++
                        Log.d("表示orLOD遅延",  "marker${it.index + 1} LOD更新キャンセル = ${updateLODCancel} ${System.currentTimeMillis()}")
                    } else if (nowDownloadingLods["marker${it.index + 1}"]?.level!! >= it.level) {
                        return@forEach
                    }
                } // 現在通信中ならキャンセルしてこの後新しく再リクエスト
                scheduledFetchLOD.add(it)
                objectsProcessed.add(it.index)
                objectInfoList["marker${it.index + 1}"]!!.prevLOD = objectInfoList["marker${it.index + 1}"]!!.currentLOD
            }
            Log.d("allLODList", "$sortedList")

            scheduledFetchLOD.forEach {
                GlobalScope.launch(Dispatchers.Main) {
                    val prevLevel = objectInfoList["marker${it.index + 1}"]!!.currentLOD
                    Log.d("allLODList", "marker${it.index + 1}, level=${it.LODLevel.level}の処理")

                    objectInfoList["marker${it.index + 1}"]!!.currentLOD = it.LODLevel.level.toInt() // LODレベルの記録を更新
                    objectNowLevelAndNextLevel[it.index]?.next = it.LODLevel.level
                    nowDownloadingLods["marker${it.index + 1}"] = it // ダウンロード中のLODのリストに追加
                    val isComplete = fetchObject("marker${it.index + 1}", it.LODLevel.level)
                    if (!isComplete) {
//                        objectInfoList["marker${it.index + 1}"]!!.currentLOD = objectInfoList["marker${it.index + 1}"]!!.prevLOD
//                        Log.d("wiwiwiwiwiw", "marker${it.index + 1}: ${objectInfoList["marker${it.index + 1}"]!!.currentLOD}")
                    }
                }
            }
//            Log.d("allLODList", "スケジュール: $scheduledFetchLOD")
//            Log.d("allLODList", "ダウンロード中: ${nowDownloadingLods}")
//            Log.d("allLODList", "${count}回目のlodselection終了")
        }


        // 予想位置の範囲内にあるオブジェクトを取得
        fun getPositionOfPredictedObjects(cameraPose: Pose):  List<Int>{
            var predictedObjectKey = mutableListOf<Int>()
            val allowableRange = 5.5f // 5.5m以内のものを抽出
            val amountChangePredicted = Triple(
                predictedPosition.first - lastPredictedPosition.first,
                predictedPosition.second - lastPredictedPosition.second,
                predictedPosition.third - lastPredictedPosition.third
            )
            predictedCameraPosition = Triple(
                cameraPose.tx() + amountChangePredicted.first,
                cameraPose.ty() + amountChangePredicted.second,
                cameraPose.tz() + amountChangePredicted.third
            )
            poses.forEachIndexed { index, pose ->
                val x = predictedCameraPosition.first - pose.tx()
                val y = predictedCameraPosition.second - pose.ty()
                val z = predictedCameraPosition.third - pose.tz()
//                val x = cameraPose.tx() - pose.tx()
//                val y = cameraPose.ty() - pose.ty()
//                val z = cameraPose.tz() - pose.tz()

                val distance = sqrt(x * x + y * y + z * z)
                val markerString = "marker${index + 1}"
                objectInfoList[markerString]?.distance = distance

                // 範囲内なら通常処理
                if (distance <= allowableRange) {
                    val priority = getPriorityOfObject(markerString, distance, pose)

                    objectInfoList[markerString]?.priority = priority

//                    if (priority > 0) {
                    predictedObjectKey.add(index)
//                    }
//                    else if (priority == 0L && !isInFieldOfView(pose, true)) { // 優先度が0で現在視野外で
//                        if (nowDownloadingLods.containsKey(markerString)) { // 現在ダウロード中で
//                            // 70%ダウンロード完了してるならキャンセルしない
//                            if (!Quic.isDownloadProgressReached(markerString, 70L, nowDownloadingLods[markerString]?.LODLevel?.level!!)) {
//                                Log.d("cancelTest", "${markerString}: 視野外キャンセル対象")
//                                if (experimental_type == 4) {
//                                    cancelStream(markerString)
////                                    httpCancelStream(key)
//                                }
//                            }
//                        }
//                    }
                }
            }

            return predictedObjectKey
        }

        fun getNowPriorityOfObject(markerString: String, distance: Float, objectPose: Pose): Long {
            if (experimental_type != 2) {
                if (!isInFieldOfView(objectPose, true)) {
                    return 0 // 視野外のオブジェクトは優先度0（フェッチしない）
                }
            }
//            Log.d("distanceee", distance.toString())

            if (distance < 1.2) {
                val priority = 7L
                return priority
            } else if (distance < 2.0) {
                val priority = 6L
                return priority
            } else if (distance < 2.8) {
                val priority = 5L
                return priority
            } else if (distance < 3.6) {
                val priority = 4L
                return priority
            } else if (distance < 4.4) {
                val priority = 3L
                return priority
            } else if (distance < 5.2) {
                val priority = 2L
                return priority
            } else {
                val priority = 1L
                return priority
            }
        }

        // 現在の範囲内にあるオブジェクトを取得
        fun getPositionOfNowwwwwwObjects(cameraPose: Pose): List<Int> {
            var objectKeys = mutableListOf<Int>()
            val allowableRange = 5.5f // 5.5m以内のものを抽出
            poses.forEachIndexed { index, pose ->
                val x = cameraPose.tx() - pose.tx()
                val y = cameraPose.ty() - pose.ty()
                val z = cameraPose.tz() - pose.tz()

                val distance = sqrt(x * x + y * y + z * z)
                val markerString = "marker${index + 1}"
                objectInfoListForMeasure[markerString]?.distance = distance
                // 範囲内なら処理
                if (distance <= allowableRange) {
                    val priority = getNowPriorityOfObject(markerString, distance, pose)

                    objectInfoListForMeasure[markerString]?.priority = priority
                    objectKeys.add(index)
                }
            }

            return objectKeys
        }

        fun normalizeAngle(angle: Float): Float {
            var normalized = angle % 360
            if (normalized < 0) {
                normalized += 360
            }
            return normalized
        }

        // Timer()のインスタンス生成
        val predictionTimer = Timer()
        val predictionTask = timerTask {
            val startTime = System.currentTimeMillis()
            val localCameraPose = globalCameraPose ?: return@timerTask

            Log.d("位置トラッキング", "カメラ: ${localCameraPose.tx()} ${localCameraPose.ty()} ${localCameraPose.tz()}) 2m前方: ${currentPosition.first} ${currentPosition.second} ${currentPosition.third}")

            val t = 0.6 // 0.3秒後の位置を予測
            // 位置の速度と加速度を計算
            var positionSpeed = Triple(
                (currentPosition.first - lastPosition.first) / t,
                (currentPosition.second - lastPosition.second) / t,
                (currentPosition.third - lastPosition.third) / t
            )
            var positionAcceleration = Triple(
                positionSpeed.first / t,
                positionSpeed.second / t,
                positionSpeed.third / t
            )

            // 角度の速度と加速度を計算
            val rotationSpeed = Triple(
                (currentCameraRotation.first - lastRotation.first) / t,
                (currentCameraRotation.second - lastRotation.second) / t,
                (currentCameraRotation.third - lastRotation.third) / t
            )
            val rotationAcceleration = Triple(
                rotationSpeed.first / t,
                rotationSpeed.second / t,
                rotationSpeed.third / t
            )

            // 位置の予測計算
            predictedPosition = Triple(
                (currentPosition.first + (positionSpeed.first * t) + (0.5 * positionAcceleration.first * t * t)).toFloat(),
                (currentPosition.second + (positionSpeed.second * t) + (0.5 * positionAcceleration.second * t * t)).toFloat(),
                (currentPosition.third + (positionSpeed.third * t) + (0.5 * positionAcceleration.third * t * t)).toFloat()
            )
            // 角度の予測計算
            predictedCameraRotation = Triple(
                normalizeAngle((currentCameraRotation.first + (rotationSpeed.first * t) + (0.5 * rotationAcceleration.first * t * t)).toFloat()),
                normalizeAngle((currentCameraRotation.second + (rotationSpeed.second * t) + (0.5 * rotationAcceleration.second * t * t)).toFloat()),
                normalizeAngle((currentCameraRotation.third + (rotationSpeed.third * t) + (0.5 * rotationAcceleration.third * t * t)).toFloat())
            )

            lastPosition = currentPosition
            lastRotation = currentCameraRotation

            val updatedObjectList = getPositionOfPredictedObjects(localCameraPose) // 予測更新対象の取得

            lodSelection(updatedObjectList) // 視野内LODのアップデート

            lastPredictedPosition = predictedPosition
            val endTime = System.currentTimeMillis()
            Log.d("間隔時間", "${endTime - startTime}")
        }

        // scheduleAtFixedRateメソッドの引数
        val predictionDelay: Long= 0L
        val predictionLong: Long = 300L // 0.3秒ごと

        var isInitialized by remember { mutableStateOf(false) }

        // 特定の条件が変更された時に一度だけ実行する関数
        LaunchedEffect(planeDetected) {

            // 各Quic.fetchを非同期で実行
            if (planeDetected && session != null) {
                startTime = System.currentTimeMillis()

                lastAngle = Triple(
                    cameraNode.worldRotation.x,
                    cameraNode.worldRotation.y,
                    cameraNode.worldRotation.z
                )
                lastPosition = Triple(
                    cameraNode.worldPosition.x,
                    cameraNode.worldPosition.y,
                    cameraNode.worldPosition.z
                )
                lastRotation = Triple(
                    cameraNode.worldRotation.x,
                    cameraNode.worldRotation.y,
                    cameraNode.worldRotation.z
                )

                coroutineScope {
                    launch {
//                        poses = createCancelTestPoses(globalCameraPose!!)
//                        poses = slideTest(globalCameraPose!!)
                        poses = createAllPoses(globalCameraPose!!)
//                        poses = createOneObject(globalCameraPose!!)
//                        poses = latencyObject(globalCameraPose!!)
//                        Log.d("qposeSizse", poses.size.toString())

                        poses.forEachIndexed { index, pose ->
                            launch {
                                objectNowLevelAndNextLevel[index] = NowAndNextLevel(1, 0)
                                // 新しく追加したオブジェクトのインデックスを記録する
                                objectInfoList["marker${index + 1}"] = ObjectInfo()
                                objectInfoListForMeasure["marker${index + 1}"] = ObjectInfo()
                                if (isInFieldOfView(pose, true)) {
                                    val prevLevel = objectInfoList["marker${index + 1}"]!!.currentLOD
                                    objectInfoList["marker${index + 1}"]!!.currentLOD = 1 // LODレベルの記録を更新
                                    nowDownloadingLods["marker${index + 1}"] = FetchObjectInfo(
                                        index,
                                        1,
                                        0f,
                                        objectInfoList["marker${index + 1}"]!!.lodLevelGroup[0]
                                    ) // ダウンロード中のLODのリストに追加
                                    val isComplete = fetchObject("marker${index + 1}", 1)
                                    if (!isComplete) {
                                        objectInfoList["marker${index + 1}"]!!.currentLOD = prevLevel
                                    } else {
                                        objectNowLevelAndNextLevel[index]?.next = 1
                                    }
//                                    Log.d("cancelTest", "${"marker${index + 1}"}: 初期fetch")
                                }
                            }
                        }
                    }
                }

                predictionTimer.scheduleAtFixedRate(predictionTask, predictionDelay, predictionLong)
                estimateTimer.scheduleAtFixedRate(estimateTask, estimateDelay, estimateLong)

                isInitialized = true
            }
        }

        fun calculateDownloadingTimeOfLODForMeasure(movingAverage: Float, objectInfo: ObjectInfo) {
            objectInfo.lodLevelGroup.forEach {
                if (movingAverage == 0f) {
                    it.estimateDownloadingTime = 0f
                } else {
                    it.estimateDownloadingTime = it.fileSize / movingAverage
                }
            }
        }

        fun nowLodSelection(objectList: List<Int>, nowTime: Long): List<FetchObjectInfo> {
            var movingAverage = getMovingAverage() // 直近5秒の移動平均を取得
            var allLODList = mutableListOf<FetchObjectInfo>() // オブジェクトIDとpriority * utilityのマップ
            objectList.forEachIndexed { index, id ->
                val key = "marker${id + 1}"
                calculateDownloadingTimeOfLODForMeasure(movingAverage, objectInfoListForMeasure[key]!!) // ダウンロード時間の推定

                val priority = objectInfoListForMeasure[key]?.priority // そのオブジェクトの優先度を取得

                if (priority != 0L) {
                    for (i in 0 .. 6) { // 各LODのSSEを計算
                        objectInfoListForMeasure[key]!!.lodLevelGroup[i].sse = calculateSSE(objectInfoListForMeasure[key]!!.distance, objectInfoListForMeasure[key]!!.lodLevelGroup[i].geometricError)
                    }
                    for (i in objectInfoListForMeasure[key]!!.currentLOD..7) { // priority * utilityを計算
                        val value = calculateUtility(objectInfoListForMeasure[key]!!, i.toLong()) * priority!!
                        val newFetchObjectInfo = FetchObjectInfo(
                            index,
                            i.toLong(),
                            value,
                            objectInfoListForMeasure[key]!!.lodLevelGroup[i - 1]
                        )
                        allLODList.add(newFetchObjectInfo)
                    }
                    if (!previousVisibilityMap[key]!!) {
                        previousVisibilityMap[key] = true // 今回初めて必要になったならtureにする
                        insightTimeMap[key] = nowTime // 時間を記録
//                        Log.d("表示orLOD遅延", "${key} 視界内 ${nowTime}")
                    }
                } else { // priority = 0は視界外
                    if (previousVisibilityMap[key]!!) { // 前回まで視野内だったなら初期化
                        previousVisibilityMap[key] = false
                        insightTimeMap[key] = 0
//                        Log.d("表示orLOD遅延", "${key} 視界外 ${nowTime}")
                    }
                }
            }

            // priority * utilityでソート
            val sortedList = allLODList.sortedByDescending { it.value }

            val objectsProcessed = mutableSetOf<Int>()
            var scheduledFetchLOD = mutableListOf<FetchObjectInfo>()
            sortedList.forEach {
                if (it.index in objectsProcessed) return@forEach // もうそのオブジェクトについては選択されてる場合スキップ
//                if (objectInfoListForMeasure["marker${it.index + 1}"]!!.currentLOD >= it.LODLevel.level.toInt()) return@forEach // 本物の方での現在のLODの方が高かったらスキップ
//                if (it.value == 0f) return@forEach // valueが0の場合、それ以上今は上げる必要がないからスキップ
                scheduledFetchLOD.add(it)
                objectsProcessed.add(it.index)
            }

            return scheduledFetchLOD
        }

        var frameCount = 0

        ARScene(
            modifier = Modifier.fillMaxSize(),
            childNodes = childNodes,
            engine = engine,
            view = view,
            modelLoader = modelLoader,
            collisionSystem = collisionSystem,
            sessionConfiguration = { session, config ->  // session: ARシステムの状態の管理  config: セッションの設定を保持
                config.depthMode =
                    when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        true -> Config.DepthMode.AUTOMATIC
                        else -> Config.DepthMode.DISABLED
                    }
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                config.lightEstimationMode =
                    Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            cameraNode = cameraNode,
            planeRenderer = planeRenderer,
            onTrackingFailureChanged = {
                trackingFailureReason = it
            },
            onSessionUpdated = { updatedSession, updatedFrame ->  // ARCoreシステムの状態の更新。
                frameCount ++
//                Log.d("frame count", "${frameCount}")
                if (childNodes.isEmpty()) {
                    // 平面が検出されたらplaneDetectedをtrueにして、LaunchedEffect内の処理を実行
                    if (updatedFrame.getUpdatedPlanes()
                            .firstOrNull() !== null
                    ) {
                        plane = updatedFrame.getUpdatedPlanes().firstOrNull()
                        centerPose = updatedFrame.getUpdatedPlanes()
                            .firstOrNull() { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }?.centerPose
                        updatedFrame.getUpdatedPlanes().firstOrNull()

                    }
                }

                if (!isInitialized) {
                    session = updatedSession
                    planeDetected = true
                }

                // カメラの位置を取得
                val cameraPose = updatedFrame.camera.pose
                globalCameraPose = updatedFrame.camera.pose

                // 各オブジェクトとの距離を計算
                if (planeDetected) {
                    val nowTime = System.currentTimeMillis()

                    // 現在の視界でのLODを計算
                    var nowLodList = nowLodSelection(getPositionOfNowwwwwwObjects(cameraPose), nowTime)

//                    val updatedObjectList = getPositionOfPredictedObjects(cameraPose) // 予測更新対象の取得
//
//                    lodSelection(updatedObjectList) // 視野内LODのアップデート

                    lastPredictedPosition = predictedPosition

                    poses.forEachIndexed{ index, pose ->
                        val key = "marker${index + 1}"
                        try {
                            val x = cameraPose.tx() - pose.tx()
                            val y = cameraPose.ty() - pose.ty()
                            val z = cameraPose.tz() - pose.tz()
                            val distance = sqrt(x * x + y * y + z * z)
                            objectInfoList[key]?.distance = distance
                            objectInfoListForMeasure[key]?.distance = distance

                            // オブジェクトが現在の視界に入った時の処理
                            if (isInFieldOfView(pose, true)) {

                                // キャッシュにこのマーカーのバッファーがあるか確認, あったら表示
                                if (cacheObject.containsKey(key) and (cacheObject[key] != null)) {
                                    // 表示処理
                                    displayObject(key, childNodes, engine, modelLoader, materialLoader, index, pose, session, cacheObject[key]!!)
                                    cacheObject.remove(key) // 表示したらキャッシュ削除

                                    if (objectInfoList[key]?.showed!!) { // すでに表示されてるオブジェクト
                                        Log.d("表示orLOD遅延", "${key} LOD遅延 レベル${objectInfoList[key]?.currentLOD!!} ${nowTime - insightTimeMap[key]!!} 現在=${nowTime} 前=${insightTimeMap[key]!!}")
                                    } else { // まだ表示されてなかったオブジェクト
                                        Log.d("表示orLOD遅延", "${key} 表示遅延 レベル${objectInfoList[key]?.currentLOD!!} ${nowTime - insightTimeMap[key]!!} 現在=${nowTime} 前=${insightTimeMap[key]!!}")
                                    }

                                    insightTimeMap[key] = nowTime // 表示した時間に変更

                                    objectInfoList[key]?.showed = true // 表示したからtrueにする
                                    objectInfoListForMeasure[key]?.currentLOD = objectInfoList[key]?.currentLOD!! // 表示されたら計測用のLOD計算はこれを基準にする
                                    Log.d("表示時間計測", "${key} 表示 優先度${objectInfoList[key]?.currentLOD!!} ${System.currentTimeMillis()}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ARSample", "Error processing node for $key: ${e.message}")
                        }
                    }

                    var isLODMiss = false
                    var isShowMiss = false
                    if (nowLodList.isNotEmpty()) {
                        nowLodList.forEach {
                            val key = "marker${it.index + 1}"
                            // 表示済みのやつでLODが違かったら
                            if (objectInfoListForMeasure[key]?.currentLOD!! < it.level.toInt() && objectInfoList[key]?.showed == true) {
                                Log.d("キャッシュ計測", "${key}のキャッシュミス: 現在のLOD = ${objectInfoListForMeasure[key]?.currentLOD}, 現在視点でのLOD = ${it.level}")
                                Log.d("表示時間計測", "${key} LODミス 優先度${it.level}が欲しかったけど優先度${objectInfoListForMeasure[key]?.currentLOD}だったからミス ${System.currentTimeMillis()}")
                                isLODMiss = true
                                cacheFailedTotal ++
                            }

                            // 表示されていなかったら
                            if (objectInfoList[key]?.showed == false) {
                                Log.d("キャッシュ計測", "${key}のキャッシュミス: 見えてないよ,,,,, 現在のLOD = ${objectInfoListForMeasure[key]?.currentLOD}, 現在視点でのLOD = ${it.level}")
                                Log.d("表示時間計測", "${key} 表示ミス 優先度${it.level}が欲しかったけど優先度${objectInfoListForMeasure[key]?.currentLOD}だったからミス ${System.currentTimeMillis()}")
                                isShowMiss = true
                                cacheShowMiss ++
                            }

                            cacheTotal ++
                        }
                    }

                    if (isLODMiss) {
                        totalLODMissFrame ++
                    } else if (isShowMiss) {
                        totalShowMissFrame ++
                    }
                    totalFrame ++
                    Log.d("キャッシュ計測", "フレーム = ${totalFrame}, LODミスフレーム = ${totalLODMissFrame}, 表示ミスフレーム = ${totalShowMissFrame}, トータルオブジェクト = ${cacheTotal}, LODミス = ${cacheFailedTotal}, 表示ミス = ${cacheShowMiss}")
                }

                val fixedDistance = 2f
                val cameraForward = cameraPose.zAxis
                currentPosition = Triple(
                    cameraPose.tx() - cameraForward[0] * fixedDistance,
                    cameraPose.ty() - cameraForward[1] * fixedDistance,
                    cameraPose.tz() - cameraForward[2] * fixedDistance
                )
                if (childNodes.size == numberOfObject) {
                    val currentPositionNode = SphereNode(
                        engine = engine,
                        radius = 0.05f,
                        materialInstance = materialLoader.createColorInstance(Color.Blue)
                    )
                    currentPositionNode.worldPosition = Position(
                        currentPosition.first,
                        currentPosition.second,
                        currentPosition.third
                    )

                    val predictedPositionNode = SphereNode(
                        engine = engine,
                        radius = 0.05f,
                        materialInstance = materialLoader.createColorInstance(Color.Red)
                    )
                    predictedPositionNode.worldPosition = Position(
                        predictedPosition.first,
                        predictedPosition.second,
                        predictedPosition.third
                    )

                    val poseNode1 = SphereNode(
                        engine = engine,
                        radius = 0.05f,
                        materialInstance = materialLoader.createColorInstance(Color.Yellow)
                    )
                    poseNode1.worldPosition = Position(
                        poses[0].tx(),
                        poses[0].ty(),
                        poses[0].tz()
                    )

                    childNodes += currentPositionNode
                    predictedNodeIndex1 = childNodes.size - 1
                    childNodes += predictedPositionNode
                    predictedNodeIndex2 = childNodes.size - 1
                    childNodes += poseNode1

                } else if (childNodes.size >= numberOfObject + 2) {
                    (childNodes[predictedNodeIndex1] as? SphereNode)?.worldPosition = Position(
                        cameraPose.tx() - cameraForward[0] * fixedDistance,
                        cameraPose.ty() - cameraForward[1] * fixedDistance,
                        cameraPose.tz() - cameraForward[2] * fixedDistance
                    )

                    (childNodes[predictedNodeIndex2] as? SphereNode)?.worldPosition = Position(
                        predictedPosition.first,
                        predictedPosition.second,
                        predictedPosition.third
                    )
                }

                Log.d("最終LODレベル計測", "=================================")
                objectInfoList.forEach { s, objectInfo ->
                    Log.d("最終LODレベル計測", "$s: ${objectInfo.currentLOD}")
                }
                Log.d("最終LODレベル計測", "=================================")
            }
        )
    }
}

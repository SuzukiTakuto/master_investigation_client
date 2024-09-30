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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.acos
import kotlin.math.tan

// distance: オブジェクトとユーザの距離。priority: 現在の優先度(想定外の優先度で初期化)。indexOfChildNodes: childNodesのどこにそのオブジェクトが格納されているか。

private const val kMaxModelInstances = 10
private const val numberOfObject = 2

// カメラの視野角を定義（例：水平60度、垂直45度）
private const val HORIZONTAL_FOV = 56f
private const val HORIZONTAL_FOV_RAD = HORIZONTAL_FOV * PI / 180
private const val VERTICAL_FOV = 30f

// スクリーンサイズ
private const val screenWidth = 2560
private const val screenHeight = 1600

// ターゲットSSE
private const val targetSSE = 1.5f

@Composable
fun ARSample() {
    val objectInfoList by remember { mutableStateOf(mutableMapOf<String, ObjectInfo>()) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
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
        var lastPosition by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
        var t = 2.5

        var predictedNodeIndex1 by remember {mutableStateOf<Int>(0)}
        var predictedNodeIndex2 by remember {mutableStateOf<Int>(0)}


        // オブジェクトのフェッチ処理
        // ===============================================================================
        // ===============================================================================
        // ===============================================================================
        // ===============================================================================
        // オブジェクトグループの各LODのダウンロード時間を推定
        fun calculateDownloadingTimeOfLOD(fileSize: Long, downloadingTime: Float, objectInfo: ObjectInfo) {
            objectInfo.lodLevelGroup.forEach {
                val calculatedEstimateDownloadingTime = (downloadingTime * it.fileSize) / fileSize
                it.estimateDownloadingTime = calculatedEstimateDownloadingTime
            }
        }

        fun getObject(name: String, priorityNumber: Long): Pair<Buffer, Float> {
            val result = Quic.fetch(name, priorityNumber)
            var byteArray = result.receiveData
            var downloadingTime = result.downloadingTime.toFloat()
            Log.d("byteeee", downloadingTime.toString())
            //val byteArray = Quic.httP2Fetch(name)

            val buffer = ByteBuffer.wrap(byteArray)

            return Pair(buffer, downloadingTime)
        }

        fun createAnchorNode(
            engine: Engine,
            modelLoader: ModelLoader,
            materialLoader: MaterialLoader,
            modelInstances: MutableList<ModelInstance>,
            anchor: Anchor,
            buffer: Buffer?,
        ): AnchorNode {
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
                val boundingBoxNode = CubeNode(
                    engine,
                    size = modelNode.extents,
                    center = modelNode.center,
                    materialInstance = materialLoader.createColorInstance(Color.White.copy(alpha = 0.5f))
                ).apply {
                    isVisible = false
                }
                modelNode.addChildNode(boundingBoxNode)
                anchorNode.addChildNode(modelNode)

                listOf(modelNode, anchorNode).forEach {
                    it.onEditingChanged = { editingTransforms ->
                        boundingBoxNode.isVisible = editingTransforms.isNotEmpty()
                    }
                }
                return anchorNode
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
            name: String
        ) {
            try{
                val node =
                    createAnchorNode(engine, modelLoader, materialLoader, modelInstance, anchor, buffer)

                if (childNodes.size != numberOfObject + 2) { // 最初のフェッチの時
                    childNodes += node
                    objectInfoList[name]?.indexOfChildNodes = childNodes.size - 1
                } else { // 更新のフェッチの時
                    var index = objectInfoList[name]?.indexOfChildNodes
                    childNodes[index!!].destroy()
                    childNodes[index!!] = node
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun createAnchor(pose: Pose, session: Session): Anchor {
            try{
                val anchor = session.createAnchor(pose)
                return anchor
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        suspend fun fetchAndDisplayObject(
            name: String,
            childNodes: SnapshotStateList<Node>,
            engine: Engine,
            modelLoader: ModelLoader,
            materialLoader: MaterialLoader,
            pose:  Pose?,
            session: Session?,
            priorityNumber: Long
        ) {
            try {
                val (buffer, downloadingTime) = withContext(Dispatchers.IO) { getObject(name, priorityNumber) }
                calculateDownloadingTimeOfLOD(objectInfoList[name]!!.lodLevelGroup[priorityNumber.toInt() - 1].fileSize, downloadingTime, objectInfoList[name]!!)
                buffer?.let {
                    val anchor = createAnchor(pose!!, session!!)
                    val modelInstance = mutableListOf<ModelInstance>()
                    addNodes(childNodes, modelInstance, anchor, it, engine, modelLoader, materialLoader, name)
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
//            Log.d("calculate utility", "SSE: ${(geometricError)}, quality: ${(tan(HORIZONTAL_FOV_RAD / 2))}")
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
            val sse = calculateSSE(objectInfo.distance, lod.geometricError)
            val quality = calculateQuality(sse)
            Log.d("calculate utility", "SSE: ${sse}, Quality: ${quality} selectedLOD: ${selectedLOD}")
//            if (selectedLOD != 0L) {
//                Log.d("calculate utility", "SSE: ${sse}, selectedLOD: ${quality}")
//            }

            val downloadTime = lod.fileSize / lod.estimateDownloadingTime
            return quality / (1f + downloadTime)
        }


        data class PredictedObjectInfo(
            val markerString: String,
            val pose: Pose,
            val priority: Long
        )

        // オブジェクトが視野内にあるかどうかを判断する関数
        fun isInFieldOfView(objectPose: Pose): Boolean {
            val cameraPose = globalCameraPose ?: return false

            val cameraToObject = floatArrayOf(
                objectPose.tx() - cameraPose.tx(),
                objectPose.ty() - cameraPose.ty(),
                objectPose.tz() - cameraPose.tz()
            )

            // カメラの前方ベクトルを取得
            val cameraForward = cameraPose.zAxis

            // 各ベクトルの大きさ計算
            val cameraToObjectVerticalLength = sqrt(cameraToObject[2] * cameraToObject[2] + cameraToObject[1] * cameraToObject[1])
            val cameraToObjectHorizontalLength = sqrt(cameraToObject[0] * cameraToObject[0] + cameraToObject[2] * cameraToObject[2])

            val zAxisVerticalLength = sqrt(cameraForward[2] * cameraForward[2] + cameraForward[1] * cameraForward[1])
            val zAxisHorizontalLength = sqrt(cameraForward[0] * cameraForward[0] + cameraForward[2] * cameraForward[2])

            // 内積計算
            val dotOfVertical = cameraForward[1] * cameraToObject[1] + cameraForward[2] * cameraToObject[2]
            val dotOfHorizontal = cameraForward[0] * cameraToObject[0] + cameraForward[2] * cameraToObject[2]

            // cosθ計算
            val cosOfVertical = dotOfVertical / (cameraToObjectVerticalLength * zAxisVerticalLength)
            val cosOfHorizontal = dotOfHorizontal / (cameraToObjectHorizontalLength * zAxisHorizontalLength)

            // 逆余弦関数(180°大きい値が出るからその分引く)
            val verticalAngle = 180 - Math.toDegrees(acos(cosOfVertical).toDouble())
            val horizontalAngle = 180 - Math.toDegrees(acos(cosOfHorizontal).toDouble())

            // 視野角の半分と比較
            return horizontalAngle <= HORIZONTAL_FOV / 2 && verticalAngle <= VERTICAL_FOV / 2
        }

        // オブジェクトの優先度を取得
        fun getPriorityOfObject(markerString: String, distance: Float, objectPose: Pose): Long {
            if (!isInFieldOfView(objectPose)) {
                return 0 // 視野外のオブジェクトは優先度0（フェッチしない）
            }

            if (distance < 1.5) {
                val priority = 7L
                Log.d("allLODList", "${objectInfoList[markerString]!!.priority}")
                if (objectInfoList[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                objectInfoList[markerString]!!.priority = priority
                Log.d("allLODList", "${distance}")
                return priority
            } else if (distance < 1.8) {
                val priority = 6L
                if (objectInfoList[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                objectInfoList[markerString]!!.priority = priority
                return priority
            } else if (distance < 2.1) {
                val priority = 5L
                if (objectInfoList[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                objectInfoList[markerString]!!.priority = priority
                return priority
            } else if (distance < 2.3) {
                val priority = 4L
                if (objectInfoList[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                objectInfoList[markerString]!!.priority = priority
                return priority
            } else if (distance < 2.6) {
                val priority = 3L
                if (objectInfoList[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                objectInfoList[markerString]!!.priority = priority
                return priority
            } else if (distance < 2.9) {
                val priority = 2L
                if (objectInfoList[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                objectInfoList[markerString]!!.priority = priority
                return priority
            } else {
                val priority = 1L
                if (objectInfoList[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                objectInfoList[markerString]!!.priority = priority
                return priority
            }
        }

        data class FetchObjectInfo(
            val index: Int,
            val level : Long,
            val value: Float,
            val LODLevel: LODLevel
        )

        // フェッチするオブジェクトLODの決定
        fun lodSelection() {
            var remainingTime = 2f
            var allLODList = mutableListOf<FetchObjectInfo>() // オブジェクトIDとpriority * utilityのマップ
            poses.forEachIndexed { index, pose ->
                val key = "marker${index + 1}"
                val distance = objectInfoList[key]?.distance

                val priority = getPriorityOfObject(key, distance!!, pose) // そのオブジェクトの優先度を取得

                if (priority != 0L) {
                    for (i in objectInfoList[key]!!.currentLOD..7) {  // priority * utilityを計算
                        val value = calculateUtility(objectInfoList[key]!!, i.toLong()) * priority
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

            // priority * utilityでソート
            val sortedList = allLODList.sortedByDescending { it.value }

            val objectsProcessed = mutableSetOf<Int>()
            var scheduledFetchLOD = mutableListOf<FetchObjectInfo>()
            sortedList.forEach {
                Log.d("allLODList", "${it}")
                if (it.index in objectsProcessed) return@forEach
                if (it.LODLevel.estimateDownloadingTime <= remainingTime) {
                    scheduledFetchLOD.add(it)
                    remainingTime -= it.LODLevel.estimateDownloadingTime
                    Log.d("allLODList", "残り時間: ${remainingTime}")
                    objectsProcessed.add(it.index)
                }
            }

            scheduledFetchLOD.forEach {
                GlobalScope.launch(Dispatchers.Main) {
                    fetchAndDisplayObject(
                        "marker${it.index + 1}",
                        childNodes,
                        engine,
                        modelLoader,
                        materialLoader,
                        poses[it.index],
                        session,
                        it.LODLevel.level
                    )
                }
            }
        }


        // 予想位置の範囲内にあるオブジェクトを取得
        fun getPositionOfPredictedObjects():  List<PredictedObjectInfo>{
            var predictedObjectInfo = mutableListOf<PredictedObjectInfo>()
            val allowableRange = 0.2f // 20cm以内のものを抽出
            poses.forEachIndexed { index, pose ->
                val x = predictedPosition.first - pose.tx()
                val y = predictedPosition.second - pose.ty()
                val z = predictedPosition.third - pose.tz()
                val distance = sqrt(x * x + y * y + z * z)
                val markerString = "marker${index + 1}"
                if (distance <= allowableRange) {
                    val priority = getPriorityOfObject(markerString, distance, pose)
                    val objectInfo = PredictedObjectInfo(markerString, pose, priority)
                    predictedObjectInfo.add(objectInfo)
                }
            }

            return predictedObjectInfo
        }

        // 予測更新対象のオブジェクトのアップデート
        fun updatePredictionObjects() {
            val predictedObjectInfo = getPositionOfPredictedObjects()
            predictedObjectInfo.forEach{ (markerString, pose, priority) ->
                if (priority == 0L) return@forEach
                GlobalScope.launch(Dispatchers.Main)  {
                    //fetchAndDisplayObject(markerString, childNodes, engine, modelLoader, materialLoader, pose, session, priority, objectInfo)
                }
            }
        }

        // Timer()のインスタンス生成
        val timer = Timer()
        val task = timerTask {
            val localCameraPose = globalCameraPose ?: return@timerTask

            //cameraNodeの位置と経過時間から位置を予想
            val fixedDistance = 2f
            val cameraForward = localCameraPose.zAxis
            currentPosition = Triple(
                localCameraPose.tx() - cameraForward[0] * fixedDistance,
                localCameraPose!!.ty() - cameraForward[1] * fixedDistance,
                localCameraPose!!.tz() - cameraForward[2] * fixedDistance
            )
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
            predictedPosition = Triple(
                (currentPosition.first + (positionSpeed.first * t) + (0.5 * positionAcceleration.first * t * t)).toFloat(),
                (currentPosition.second + (positionSpeed.second * t) + (0.5 * positionAcceleration.second * t * t)).toFloat(),
                (currentPosition.third + (positionSpeed.third * t) + (0.5 * positionAcceleration.third * t * t)).toFloat()
            )
            lastPosition = currentPosition

            updatePredictionObjects() // 予測更新対象のアップデート

            lodSelection() // 視野内LODのアップデート
        }

        // scheduleAtFixedRateメソッドの引数
        val delay: Long= 0L
        val Long: Long = 2500L // 2.5秒ごと

        // 特定の条件が変更された時に一度だけ実行する関数
        LaunchedEffect(planeDetected) {
            // 各Quic.fetchを非同期で実行
            if (planeDetected && centerPose != null && session != null) {
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

                coroutineScope{
                    launch {
                        // 擬似的な平面上にオブジェクトを配置
                        val planeSize = 4f // 擬似的な平面のサイズ（メートル）
                        poses = List(numberOfObject) { index ->
                            val x = (index * 0.2f + 0.4f)
                            val z = (index / 5f) * 0.5f
                            centerPose!!.let { Pose(floatArrayOf(it.tx() + x, it.ty(), it.tz() - z), floatArrayOf(it.qx(), it.qy(), it.qz(), it.qw())) } // xを-方向にすると左、zを-方向にすると奥へ配置される
                        }

                        poses.forEachIndexed { index, pose ->
                            launch {
                                // 新しく追加したオブジェクトのインデックスを記録する
                                objectInfoList["marker${index + 1}"] = ObjectInfo()
                                if (isInFieldOfView(pose)) {
                                    fetchAndDisplayObject("marker${index + 1}", childNodes, engine, modelLoader, materialLoader, pose, session, 1)
                                }
                            }
                        }
                    }
                }

                timer.scheduleAtFixedRate(task, delay, Long)
            }
        }

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
                if (childNodes.isEmpty()) {
                    // 平面が検出されたらplaneDetectedをtrueにして、LaunchedEffect内の処理を実行
                    if (updatedFrame.getUpdatedPlanes()
                            .firstOrNull() !== null
                    ) {
                        centerPose = updatedFrame.getUpdatedPlanes()
                            .firstOrNull() { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }?.centerPose
                        session = updatedSession
                        planeDetected = true
                    }
                }

                // カメラの位置を取得
                val cameraPose = updatedFrame.camera.pose
                globalCameraPose = updatedFrame.camera.pose

                // 各オブジェクトとの距離を計算
                if (planeDetected) {
                    poses.forEachIndexed{ index, pose ->
                        val key = "marker${index + 1}"
                        try {
                            val x = cameraPose.tx() - pose.tx()
                            val y = cameraPose.ty() - pose.ty()
                            val z = cameraPose.tz() - pose.tz()
                            val distance = sqrt(x * x + y * y + z * z)
                            objectInfoList[key]?.distance = distance

                            //一定の距離以内になったら
//                            val priority = getPriorityOfObject(key, distance, pose)
//                            calculateUtility(objectInfoList[key]!!, objectInfoList[key]!!.priority)
//                            if (priority != 0L) {
//                                GlobalScope.launch(Dispatchers.Main)  {
//                                    fetchAndDisplayObject(key, childNodes, engine, modelLoader, materialLoader, pose, session, priority)
//                                }
//                            }
                        } catch (e: Exception) {
                            Log.e("ARSample", "Error processing node for $key: ${e.message}")
                        }
                        //Log.d("distances", objectInfo.toString())
                    }
                }

                val fixedDistance = 2f
                val cameraForward = cameraPose.zAxis
                if (childNodes.size == numberOfObject) {
                    val currentPositionNode = SphereNode(
                        engine = engine,
                        radius = 0.05f,
                        materialInstance = materialLoader.createColorInstance(Color.Blue)
                    )
                    currentPositionNode.worldPosition = Position(
                        cameraPose.tx() - cameraForward[0] * fixedDistance,
                        cameraPose.ty() - cameraForward[1] * fixedDistance,
                        cameraPose.tz() - cameraForward[2] * fixedDistance
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

                    childNodes += currentPositionNode
                    predictedNodeIndex1 = childNodes.size - 1
                    childNodes += predictedPositionNode
                    predictedNodeIndex2 = childNodes.size - 1
                } else if (childNodes.size >= numberOfObject + 2) {
                    (childNodes[predictedNodeIndex1] as? SphereNode)?.worldPosition = Position(
                        cameraPose.tx() - cameraForward[0] * fixedDistance,
                        cameraPose.ty() - cameraForward[1] * fixedDistance,
                        cameraPose.tz() - cameraForward[2] * fixedDistance
                    )
//
                    (childNodes[predictedNodeIndex2] as? SphereNode)?.worldPosition = Position(
                        predictedPosition.first,
                        predictedPosition.second,
                        predictedPosition.third
                    )
                }
            }
        )
    }
}

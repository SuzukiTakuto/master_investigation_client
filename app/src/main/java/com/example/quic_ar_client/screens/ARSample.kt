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

// distance: オブジェクトとユーザの距離。priority: 現在の優先度。indexOfChildNodes: childNodesのどこにそのオブジェクトが格納されているか。
data class DistanceAndPriority(var distance: Float = 0f, var priority: Long = 7, var indexOfChildNodes: Int = 0)

private const val kMaxModelInstances = 10
private const val numberOfObject = 4

@Composable
fun ARSample() {
    val distancesAndPriority by remember { mutableStateOf(mutableMapOf<String, DistanceAndPriority>()) }

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
        var t = 0.5

        var debugNodes by remember {mutableStateOf(listOf<Node>())} // デバッグ用ノード

        var predictedNodeIndex1 by remember {mutableStateOf<Int>(0)}
        var predictedNodeIndex2 by remember {mutableStateOf<Int>(0)}

        data class PredictedObjectInfo(
            val markerString: String,
            val pose: Pose,
            val priority: Long
        )

        // カメラの視野角を定義（例：水平60度、垂直45度）
        val HORIZONTAL_FOV = 55f
        val VERTICAL_FOV = 35f

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

        fun getPriorityOfObject(markerString: String, distance: Float, objectPose: Pose): Long {
            if (!isInFieldOfView(objectPose)) {
                return 0 // 視野外のオブジェクトは優先度0（フェッチしない）
            }

            if (distance < 1.5) {
                val priority = 1L
                if (distancesAndPriority[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                distancesAndPriority[markerString]!!.priority = priority
                return priority
            } else if (distance < 1.8) {
                val priority = 2L
                if (distancesAndPriority[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                distancesAndPriority[markerString]!!.priority = priority
                return priority
            } else if (distance < 2.1) {
                val priority = 3L
                if (distancesAndPriority[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                distancesAndPriority[markerString]!!.priority = priority
                return priority
            } else if (distance < 2.3) {
                val priority = 4L
                if (distancesAndPriority[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                distancesAndPriority[markerString]!!.priority = priority
                return priority
            } else if (distance < 2.6) {
                val priority = 5L
                if (distancesAndPriority[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                distancesAndPriority[markerString]!!.priority = priority
                return priority
            } else if (distance < 2.9) {
                val priority = 6L
                if (distancesAndPriority[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                distancesAndPriority[markerString]!!.priority = priority
                return priority
            } else if (distance < 3.1) {
                val priority = 7L
                if (distancesAndPriority[markerString]!!.priority <= priority) return 0 // 既にその優先度以下ならスキップ
                distancesAndPriority[markerString]!!.priority = priority
                return priority
            } else {
                return 0
            }
        }

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

        fun updatePredictionObjects() {
            val predictedObjectInfo = getPositionOfPredictedObjects()
            predictedObjectInfo.forEach{ (markerString, pose, priority) ->
                if (priority == 0L) return
                GlobalScope.launch(Dispatchers.Main)  {
                    //fetchAndDisplayObject(markerString, childNodes, engine, modelLoader, materialLoader, pose, session, priority, distancesAndPriority)
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

            updatePredictionObjects()
        }

        // scheduleAtFixedRateメソッドの引数
        val delay: Long= 0L
        val Long: Long = 300L

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
                timer.scheduleAtFixedRate(task, delay, Long)

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
                                distancesAndPriority["marker${index + 1}"] = DistanceAndPriority()
                                if (isInFieldOfView(pose)) {
                                    fetchAndDisplayObject("marker${index + 1}", childNodes, engine, modelLoader, materialLoader, pose, session, 7, distancesAndPriority)
                                }
                            }
                        }
                    }
                }
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
                if (!childNodes.isEmpty()) {
                    poses.forEachIndexed{ index, pose ->
                        val key = "marker${index + 1}"
                        try {
                            val x = cameraPose.tx() - pose.tx()
                            val y = cameraPose.ty() - pose.ty()
                            val z = cameraPose.tz() - pose.tz()
                            val distance = sqrt(x * x + y * y + z * z)
                            distancesAndPriority[key]?.distance = distance

                            //一定の距離以内になったら
                            val priority = getPriorityOfObject(key, distance, pose)
                            Log.d("pose!!!!", pose.toString())
                            if (priority != 0L) {
                                GlobalScope.launch(Dispatchers.Main)  {
                                    fetchAndDisplayObject(key, childNodes, engine, modelLoader, materialLoader, pose, session, priority, distancesAndPriority)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ARSample", "Error processing node for $key: ${e.message}")
                        }
                        //Log.d("distances", distancesAndPriority.toString())
                    }
                }

                // 視線予測デバッグ用
                fun Position.normalized(): Position {
                    val length = sqrt(x * x + y * y + z * z)
                    return if (length > 0) Position(x / length, y / length, z / length) else this
                }

                fun Position.cross(other: Position): Position {
                    return Position(
                        y * other.z - z * other.y,
                        z * other.x - x * other.z,
                        x * other.y - y * other.x
                    )
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

suspend fun fetchAndDisplayObject(
    name: String,
    childNodes: SnapshotStateList<Node>,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    pose:  Pose?,
    session: Session?,
    priorityNumber: Long,
    distancesAndPriority: MutableMap<String, DistanceAndPriority>
) {
    try {
        val buffer = withContext(Dispatchers.IO) { getObject(name, priorityNumber) }
        buffer?.let {
            val anchor = createAnchor(pose!!, session!!)
            val modelInstance = mutableListOf<ModelInstance>()
            addNodes(childNodes, modelInstance, anchor, it, engine, modelLoader, materialLoader, name, distancesAndPriority)
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

fun addNodes(
    childNodes: SnapshotStateList<Node>,
    modelInstance: MutableList<ModelInstance>,
    anchor: Anchor,
    buffer: Buffer,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    name: String,
    distancesAndPriority: MutableMap<String, DistanceAndPriority>
) {
    try{
        val node =
            createAnchorNode(engine, modelLoader, materialLoader, modelInstance, anchor, buffer)

        if (childNodes.size != numberOfObject + 2) { // 最初のフェッチの時
            childNodes += node
            distancesAndPriority[name]?.indexOfChildNodes = childNodes.size - 1
        } else { // 更新のフェッチの時
            var index = distancesAndPriority[name]?.indexOfChildNodes
            childNodes[index!!].destroy()
            childNodes[index!!] = node
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
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

fun getObject(name: String, priorityNumber: Long): Buffer {
    val byteArray = Quic.fetch(name, priorityNumber)
    //val byteArray = Quic.httP2Fetch(name)

    val buffer = ByteBuffer.wrap(byteArray)

    return buffer
}

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
import kotlinx.coroutines.joinAll
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val mutex = Mutex()

// distance: オブジェクトとユーザの距離。priority: 現在の優先度。indexOfChildNodes: childNodesのどこにそのオブジェクトが格納されているか。
data class DistanceAndPriority(var distance: Float = 0f, var priority: Long = 7, var indexOfChildNodes: Int = 0)

private const val kMaxModelInstances = 10

@Composable
fun ARSample(sensorData: Triple<Float, Float, Float>) {
    var distancesAndPriority by remember { mutableStateOf(mutableMapOf<String, DistanceAndPriority>()) }

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
        var session by remember { mutableStateOf<Session?>(null) }

        var trackingFailureReason by remember {
            mutableStateOf<TrackingFailureReason?>(null) // ARトラッキングの失敗についての情報を格納。 ex. BAD_STATE, CAMERA_UNAVAILABLE, EXCESSIVE_MOTION
        }

        var planeDetected by remember { mutableStateOf(false) }

        var poses by remember {mutableStateOf(emptyList<Pose>())}

        var currentAngle by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var predictedAngle by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var lastAngle by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
        var currentPosition by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var predictedPosition by remember {mutableStateOf(Triple(0f, 0f, 0f))}
        var lastPosition by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
        var t = 0.2

        var debugNodes by remember {mutableStateOf(listOf<Node>())} // デバッグ用ノード

        var predictedNodeIndex1 by remember {mutableStateOf<Int>(0)}
        var predictedNodeIndex2 by remember {mutableStateOf<Int>(0)}

        // Timer()のインスタンス生成
        val timer = Timer()
        val task = timerTask {
            // cameraNodeの角度と経過時間から角度を予想
            currentAngle = Triple(
                cameraNode.worldRotation.x,
                cameraNode.worldRotation.y,
                cameraNode.worldRotation.z
            )
            var angularSpeed = Triple(
                (currentAngle.first - lastAngle.first) / t,
                (currentAngle.second - lastAngle.second) / t,
                (currentAngle.third - lastAngle.third) / t
            )
            var angularAcceleration = Triple(
                angularSpeed.first / t,
                angularSpeed.second / t,
                angularSpeed.third / t
            )
            predictedAngle = Triple(
                (currentAngle.first + (angularSpeed.first * t) + (0.5 * angularAcceleration.first * t * t)).toFloat(),
                (currentAngle.second + (angularSpeed.second * t) + (0.5 * angularAcceleration.second * t * t)).toFloat(),
                (currentAngle.third + (angularSpeed.third * t) + (0.5 * angularAcceleration.third * t * t)).toFloat(),
            )
            lastAngle = currentAngle

            //cameraNodeの位置と経過時間から位置を予想
            currentPosition = Triple(
                cameraNode.worldPosition.x,
                cameraNode.worldPosition.y,
                cameraNode.worldPosition.z
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
                        poses = List(4) { index ->
                            val x = (index * 0.2f + 0.4f)
                            val z = (Random.nextFloat() - 1.5f) * planeSize
                            centerPose!!.let { Pose(floatArrayOf(it.tx() + x, it.ty(), it.tz()), floatArrayOf(it.qx(), it.qy(), it.qz(), it.qw())) } // xを-方向にすると左、zを-方向にすると奥へ配置される
                        }

                        val jobs = poses.mapIndexed { index, pose ->
                            launch {
                                fetchAndDisplayObject("marker${index + 1}", childNodes, engine, modelLoader, materialLoader, pose, session, 7, distancesAndPriority)
                            }
                        }
                        jobs.joinAll()
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
                // カメラの位置を取得
                val cameraPose = updatedFrame.camera.pose
                //Log.d("cameraRotation", cameraNode.rotation.toString())

                // カメラの前方向きのベクトルを取得
                val forwardVector = cameraPose.zAxis

                // カメラから任意の距離（例：2メートル）前方の位置を計算
                val distanceFromCamera = -2f
                val pseudoPlaneCenter = cameraPose.compose(Pose.makeTranslation(
                    forwardVector[0] * distanceFromCamera,
                    forwardVector[1],
                    forwardVector[2] * distanceFromCamera
                ))

                if (childNodes.isEmpty()) {
                    // 擬似的な平面が作成されたらplaneDetectedをtrueにして、LaunchedEffect内の処理を実行
                    centerPose = pseudoPlaneCenter
                    session = updatedSession
                    planeDetected = true
                }

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

                            var indexOfChildNodes = distancesAndPriority[key]?.indexOfChildNodes
                            // 一定の距離以内になったら
//                            if (distance < 1.0) {
//                                if (distancesAndPriority[key]!!.priority <= 1) return@forEachIndexed // 既にその優先度以下ならスキップ
//                                distancesAndPriority[key]!!.priority = 1
//                                // 既存ノードの削除と新しいノードの追加を同期的に行う
//                                GlobalScope.launch(Dispatchers.Main)  {
//                                    childNodes[indexOfChildNodes!!].destroy() // そのオブジェクトを削除
//                                    fetchAndDisplayObject("marker${index + 1}", childNodes, engine, modelLoader, materialLoader, pose, session, 1, distancesAndPriority)
//
//                                }
//                            } else if (distance < 1.5) {
//                                if (distancesAndPriority[key]!!.priority <= 3) return@forEachIndexed // 既にその優先度以下ならスキップ
//                                distancesAndPriority[key]!!.priority = 3
//                                // 既存ノードの削除と新しいノードの追加を同期的に行う
//                                GlobalScope.launch(Dispatchers.Main)  {
//                                    childNodes[indexOfChildNodes!!].destroy() // そのオブジェクトを削除
//                                    fetchAndDisplayObject("marker${index + 1}", childNodes, engine, modelLoader, materialLoader, pose, session, 3, distancesAndPriority)
//                                }
//                            }
                        } catch (e: Exception) {
                            Log.e("ARSample", "Error processing node for $key: ${e.message}")
                        }
                        Log.d("distances", distancesAndPriority.toString())
                    }
                }

                // 視線予測デバッグ用
                // onSessionUpdatedの中で以下のコードを追加
                Log.d("pseudoPlaneCenter", pseudoPlaneCenter.toString())

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

//                fun createRotationFromDirection(direction: Position, up: Position): Rotation {
//                    val dir = direction.normalized()
//                    val right = up.cross(dir).normalized()
//                    val newUp = dir.cross(right)
//
//                    return Rotation(
//                        right.x, right.y, right.z,
//                        newUp.x, newUp.y, newUp.z,
//                        dir.x, dir.y, dir.z
//                    ).toQuaternion()
//                }
//
//                // 2点間に線（細長い円柱）を作成する関数
//                fun createLineNode(
//                    engine: Engine,
//                    materialLoader: MaterialLoader,
//                    start: Position,
//                    end: Position,
//                    color: Color,
//                    radius: Float = 0.005f
//                ): Node {
//                    val direction = Position(end.x - start.x, end.y - start.y, end.z - start.z)
//                    val distance = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
//                    val center = Position(
//                        start.x + direction.x * 0.5f,
//                        start.y + direction.y * 0.5f,
//                        start.z + direction.z * 0.5f
//                    )
//
//                    val lineNode = CylinderNode(
//                        engine = engine,
//                        radius = radius,
//                        height = distance,
//                        materialInstance = materialLoader.createColorInstance(color)
//                    )
//
//                    lineNode.worldPosition = center
//                    lineNode.worldRotation = createRotationFromDirection(direction, Position(0f, 1f, 0f))
//
//                    return lineNode
//                }
//
//                val lineNode = createLineNode(
//                    engine,
//                    materialLoader,
//                    Position(cameraPose.tx(), cameraPose.ty(), cameraPose.tz()),
//                    Position(
//                        predictedPosition.first,
//                        predictedPosition.second,
//                        predictedPosition.third
//                    ),
//                    Color.Green
//                )

//                debugNodes = listOf(
//                    currentPositionNode,
//                    predictedPositionNode,
//                )

                Log.d("childd", childNodes.size.toString())
                if (childNodes.size == 4) {
                    val currentPositionNode = SphereNode(
                        engine = engine,
                        radius = 0.05f,
                        materialInstance = materialLoader.createColorInstance(Color.Blue)
                    )
                    currentPositionNode.worldPosition = Position(
                        pseudoPlaneCenter.tx(),
                        pseudoPlaneCenter.ty(),
                        pseudoPlaneCenter.tz()
                    )

                    childNodes += currentPositionNode
                    predictedNodeIndex1 = childNodes.size - 1
//                    childNodes += predictedPositionNode
//                    predictedNodeIndex1 = childNodes.size - 1
                } else if (childNodes.size >= 5) {
                    (childNodes[predictedNodeIndex1] as? SphereNode)?.worldPosition = Position(
                        pseudoPlaneCenter.tx(),
                        pseudoPlaneCenter.ty(),
                        pseudoPlaneCenter.tz()
                    )
//                    childNodes[predictedNodeIndex2].destroy()
//                    childNodes[predictedNodeIndex2] = predictedPositionNode
                }


//
//                childNodes +=  debugNodes
            },
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
            mutex.withLock {
                addNodes(childNodes, modelInstance, anchor, it, engine, modelLoader, materialLoader, name, distancesAndPriority)
            }
        }
    } catch (e: Exception) {
        Log.d("fetch", "Error fetching or displaying object: ${e.message}")
    }
}

fun createAnchor(pose: Pose, session: Session): Anchor {
    return session.createAnchor(pose)
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
    val node = createAnchorNode(engine, modelLoader, materialLoader, modelInstance, anchor, buffer)

    // 新しく追加したオブジェクトのインデックスを記録する
    if (!distancesAndPriority.containsKey(name)) {
        distancesAndPriority[name] = DistanceAndPriority()
    }

    if (childNodes.size != 4) { // 最初のフェッチの時
        childNodes += node
        distancesAndPriority[name]?.indexOfChildNodes = childNodes.size - 1
    } else { // 更新のフェッチの時
        var index = distancesAndPriority[name]?.indexOfChildNodes
        childNodes[index!!] = node
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
    val anchorNode = AnchorNode(engine = engine, anchor = anchor)
    val modelNode = ModelNode(
        modelInstance = modelInstances.apply {
            if (isEmpty()) {
                try {
                    Log.d("anchor", modelInstances.toString())
                    this += modelLoader.createInstancedModel(buffer = buffer!!, count = kMaxModelInstances)
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
}

fun getObject(name: String, priorityNumber: Long): Buffer {
    val byteArray = Quic.fetch(name, priorityNumber)
    //val byteArray = Quic.httP2Fetch(name)

    val buffer = ByteBuffer.wrap(byteArray)

    return buffer
}

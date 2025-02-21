package com.example.quic_ar_client.screens

import android.graphics.PointF
import android.opengl.Matrix
import kotlin.math.abs

class ARViewportChecker(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val horizontalFOV: Float,
    private val verticalFOV: Float
) {
    // スクリーン中心からの許容範囲（画面端までの距離に対する割合 0.0-1.0）
    private val toleranceRatio = 0.95f

    // ビューマトリックスとプロジェクションマトリックス
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)

    // オブジェクトが視界に入っているかチェック
    fun isObjectInViewport(
        objectPosition: Float3,
        cameraPosition: Float3,
        cameraRotation: Float3
    ): ViewportCheckResult {
        // カメラのビューマトリックスを更新
        updateViewMatrix(cameraPosition, cameraRotation)

        // オブジェクトのスクリーン座標を計算
        val screenPoint = worldToScreen(objectPosition)

        // 視界内かどうかのチェック
        val inHorizontalFOV = abs(screenPoint.x - screenWidth / 2) <=
                (screenWidth / 2) * toleranceRatio
        val inVerticalFOV = abs(screenPoint.y - screenHeight / 2) <=
                (screenHeight / 2) * toleranceRatio

        // カメラからオブジェクトまでの距離を計算
        val distance = calculateDistance(objectPosition, cameraPosition)

        // オブジェクトの方向を計算
        val direction = calculateDirection(screenPoint)

        return ViewportCheckResult(
            isInViewport = inHorizontalFOV && inVerticalFOV,
            screenPosition = screenPoint,
            distance = distance,
            direction = direction
        )
    }

    // カメラのビューマトリックスを更新
    private fun updateViewMatrix(position: Float3, rotation: Float3) {
        Matrix.setLookAtM(
            viewMatrix, 0,
            position.x, position.y, position.z,  // カメラ位置
            rotation.x, rotation.y, rotation.z,  // 注視点
            0f, 1f, 0f                          // 上方向ベクトル
        )

        // プロジェクションマトリックスを更新
        val aspect = screenWidth.toFloat() / screenHeight.toFloat()
        Matrix.perspectiveM(
            projectionMatrix, 0,
            verticalFOV,  // 視野角
            aspect,       // アスペクト比
            0.1f,        // ニアクリップ
            100.0f       // ファークリップ
        )

        // ビュープロジェクションマトリックスを計算
        Matrix.multiplyMM(
            viewProjectionMatrix, 0,
            projectionMatrix, 0,
            viewMatrix, 0
        )
    }

    // ワールド座標からスクリーン座標に変換
    private fun worldToScreen(worldPosition: Float3): PointF {
        val coords = FloatArray(4)
        // 4次元座標に変換
        coords[0] = worldPosition.x
        coords[1] = worldPosition.y
        coords[2] = worldPosition.z
        coords[3] = 1f

        // ビュープロジェクション変換
        val transformedCoords = FloatArray(4)
        Matrix.multiplyMV(transformedCoords, 0, viewProjectionMatrix, 0, coords, 0)

        // 正規化デバイス座標に変換
        val ndcX = transformedCoords[0] / transformedCoords[3]
        val ndcY = transformedCoords[1] / transformedCoords[3]

        // スクリーン座標に変換
        return PointF(
            (ndcX + 1f) * screenWidth / 2f,
            (1f - ndcY) * screenHeight / 2f
        )
    }

    // カメラからオブジェクトまでの距離を計算
    private fun calculateDistance(obj: Float3, camera: Float3): Float {
        return kotlin.math.sqrt(
            (obj.x - camera.x) * (obj.x - camera.x) +
                    (obj.y - camera.y) * (obj.y - camera.y) +
                    (obj.z - camera.z) * (obj.z - camera.z)
        )
    }

    // オブジェクトの方向を計算
    private fun calculateDirection(screenPoint: PointF): ViewportDirection {
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        return when {
            screenPoint.x < centerX && screenPoint.y < centerY -> ViewportDirection.TOP_LEFT
            screenPoint.x > centerX && screenPoint.y < centerY -> ViewportDirection.TOP_RIGHT
            screenPoint.x < centerX && screenPoint.y > centerY -> ViewportDirection.BOTTOM_LEFT
            screenPoint.x > centerX && screenPoint.y > centerY -> ViewportDirection.BOTTOM_RIGHT
            screenPoint.x == centerX.toFloat() && screenPoint.y < centerY -> ViewportDirection.TOP
            screenPoint.x == centerX.toFloat() && screenPoint.y > centerY -> ViewportDirection.BOTTOM
            screenPoint.x < centerX.toFloat() && screenPoint.y == centerY.toFloat() -> ViewportDirection.LEFT
            screenPoint.x > centerX.toFloat() && screenPoint.y == centerY.toFloat() -> ViewportDirection.RIGHT
            else -> ViewportDirection.CENTER
        }
    }

    // データクラスと列挙型の定義
    data class Float3(val x: Float, val y: Float, val z: Float)

    data class ViewportCheckResult(
        val isInViewport: Boolean,
        val screenPosition: PointF,
        val distance: Float,
        val direction: ViewportDirection
    )

    enum class ViewportDirection {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, CENTER, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }
}
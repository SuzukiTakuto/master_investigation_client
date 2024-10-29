package com.example.quic_ar_client.screens

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

class CameraFOVCalculator(private val context: Context) {

    data class CameraFOV(
        val horizontalFOV: Float,
        val verticalFOV: Float,
        val diagonalFOV: Float
    )

    fun getCameraFOV(cameraId: String = "0"): CameraFOV? {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            // センサーの方向を取得
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // カメラの視野角を取得
            characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.let { focalLengths ->
                val focalLength = focalLengths[0] // mm単位

                // センサーサイズを取得 (mm単位)
                characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { sensorSize ->

                    // 水平FOVの計算
                    val horizontalFOV = (2 * Math.atan((sensorSize.width / (2 * focalLength)).toDouble())
                            * (180 / Math.PI)).toFloat()

                    // 垂直FOVの計算
                    val verticalFOV = (2 * Math.atan((sensorSize.height / (2 * focalLength)).toDouble())
                            * (180 / Math.PI)).toFloat()

                    // 対角線FOVの計算
                    val diagonalFOV = (2 * Math.atan(
                        Math.sqrt(
                            Math.pow(sensorSize.width.toDouble(), 2.0) +
                                    Math.pow(sensorSize.height.toDouble(), 2.0)
                        ) / (2 * focalLength)
                    ) * (180 / Math.PI)).toFloat()

                    return CameraFOV(horizontalFOV, verticalFOV, diagonalFOV)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
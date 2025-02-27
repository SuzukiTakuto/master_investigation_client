package com.example.quic_ar_client.screens

import com.google.ar.core.Pose

fun createOneObject(globalCameraPose: Pose): List<Pose> {
    val poses = listOf(
        globalCameraPose!!.let { cameraPose ->
            val forward = cameraPose.zAxis
            val right = cameraPose.xAxis

            // 基準点の計算（カメラの2m前方）
            val baseX = cameraPose.tx() - forward[0] * 2.0f
            val baseY = cameraPose.ty() - forward[1] * 2.0f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f - 1

            // 以下、基準点からの相対位置で配置
            Pose(
                floatArrayOf(baseX, baseY, baseZ),
                cameraPose.rotationQuaternion
            ) // 中心
        },
        globalCameraPose!!.let { cameraPose ->
            val forward = cameraPose.zAxis
            val right = cameraPose.xAxis

            // 基準点の計算（カメラの2m前方）
            val baseX = cameraPose.tx() - forward[0] * 2.0f + 1
            val baseY = cameraPose.ty() - forward[1] * 2.0f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f - 1

            // 以下、基準点からの相対位置で配置
            Pose(
                floatArrayOf(baseX, baseY, baseZ),
                cameraPose.rotationQuaternion
            ) // 中心
        },
        globalCameraPose!!.let { cameraPose ->
            val forward = cameraPose.zAxis
            val right = cameraPose.xAxis

            // 基準点の計算（カメラの2m前方）
            val baseX = cameraPose.tx() - forward[0] * 2.0f
            val baseY = cameraPose.ty() - forward[1] * 2.0f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f - 1.5f

            // 以下、基準点からの相対位置で配置
            Pose(
                floatArrayOf(baseX, baseY, baseZ),
                cameraPose.rotationQuaternion
            ) // 中心
        },
        globalCameraPose!!.let { cameraPose ->
            val forward = cameraPose.zAxis
            val right = cameraPose.xAxis

            // 基準点の計算（カメラの2m前方）
            val baseX = cameraPose.tx() - forward[0] * 2.0f + 1
            val baseY = cameraPose.ty() - forward[1] * 2.0f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f - 1.5f

            // 以下、基準点からの相対位置で配置
            Pose(
                floatArrayOf(baseX, baseY, baseZ),
                cameraPose.rotationQuaternion
            ) // 中心
        }
    )

    return poses
}
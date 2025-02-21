package com.example.quic_ar_client.screens

import com.google.ar.core.Pose

fun createCancelTestPoses(globalCameraPose: Pose): List<Pose> {
    val poses = listOf(
        globalCameraPose!!.let { cameraPose ->
            val forward = cameraPose.zAxis
            val right = cameraPose.xAxis

            // 基準点の計算（カメラの2m前方）
            val baseX = cameraPose.tx() - forward[0] * 2.0f -2.4f
            val baseY = cameraPose.ty() - forward[1] * 2.0f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f - 2.4f
            val baseY = cameraPose.ty() - forward[1] * 2.0f + 0.1f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f - 2.4f
            val baseY = cameraPose.ty() - forward[1] * 2.0f - 0.1f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f - 2.3f
            val baseY = cameraPose.ty() - forward[1] * 2.0f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f + 0.5f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f - 2.3f
            val baseY = cameraPose.ty() - forward[1] * 2.0f + 0.1f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f + 0.5f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f + 2.4f
            val baseY = cameraPose.ty() - forward[1] * 2.0f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f + 0.2f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f + 2.4f
            val baseY = cameraPose.ty() - forward[1] * 2.0f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f + 0.2f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f + 2.2f
            val baseY = cameraPose.ty() - forward[1] * 2.0f - 0.1f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f + 2.3f
            val baseY = cameraPose.ty() - forward[1] * 2.0f - 0.1f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f + 0.5f

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
            val baseX = cameraPose.tx() - forward[0] * 2.0f + 2.3f
            val baseY = cameraPose.ty() - forward[1] * 2.0f + 0.1f
            val baseZ = cameraPose.tz() - forward[2] * 2.0f + 0.5f

            // 以下、基準点からの相対位置で配置
            Pose(
                floatArrayOf(baseX, baseY, baseZ),
                cameraPose.rotationQuaternion
            ) // 中心
        }
    )

    return poses
}
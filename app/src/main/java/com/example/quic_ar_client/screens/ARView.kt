package com.example.quic_ar_client.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.ar.core.Config.LightEstimationMode
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes


@Composable
fun ARView() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val cameraNode = rememberARCameraNode(engine = engine).apply {
        position = Position(z = 0.0f, x = 0.0f)  // ユーザーの初期位置
    }


    Box(modifier = Modifier.fillMaxSize()){
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = rememberNodes {
                add(
                    ModelNode(
                        modelInstance = modelLoader.createModelInstance("models/Dog.glb"),
                        //scaleToUnits = 2f,
                    ).apply {
                        //position = Position(z = -29.0f, x = 4.0f, y = -9.0f)
                    }
                )
            },
            cameraNode = cameraNode
        )
    }
}
package com.example.quic_ar_client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.quic_ar_client.screens.HomeScreen
import com.example.quic_ar_client.ui.theme.Quic_ar_clientTheme
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context
import android.util.Log
import quic.Quic

class MainActivity : ComponentActivity(),  SensorEventListener{
//    private var initialAngle = Triple(0f, 0f, 0f)
//    private var currentAngle = Triple(0f, 0f, 0f)
//    private var isInitialized = false
//
//    private val _accelerometerSensorData = mutableStateOf(Triple(0f, 0f, 0f))
//    private val _gyroscopeSensorData = mutableStateOf(Triple(0f, 0f, 0f))
//
//    private lateinit var sensorManager: SensorManager
//    private var accelerometer: Sensor? = null
//    private var gyroscope: Sensor? = null
//    private var magnetic: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
//        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
//        magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        setContent {
            Quic.connect()
            Quic_ar_clientTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HomeScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        accelerometer?.also { sensor ->
//            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
//        }
//        gyroscope?.also { sensor ->
//            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
//        }
//        magnetic?.also { sensor ->
//            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
//        }
    }

    override fun onPause() {
        super.onPause()
        //sensorManager.unregisterListener(this)
    }

//    private val THRESHOLD = 0.1f
//    private var lastAccelerometerValue = Triple(0f, 0f, 0f)
//    private val GYRO_THRESHOLD = 0.05f
//    private var lastGyroValue = Triple(0f, 0f, 0f)
//    private var lastGyroTime: Long = 0 // 角速度を微分し角加速度を得るために使用
//    private var angularAcceleration = Triple(0f, 0f, 0f)
//
//    var gravity: FloatArray? = null
//    var geomagnetic: FloatArray? = null
//    var rotationMatrix = FloatArray(9)
//    var attitude = FloatArray(3)
//    var RAD2DEG = 180/Math.PI;
//
    override fun onSensorChanged(event: SensorEvent) {
//        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
//            gravity = floatArrayOf(event.values[0], event.values[1], event.values[2])
//        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
//            geomagnetic = floatArrayOf(event.values[0], event.values[1], event.values[2])
//        }
//
//        if(geomagnetic != null && gravity != null){
//            SensorManager.getRotationMatrix(
//                rotationMatrix, null,
//                gravity, geomagnetic);
//
//            SensorManager.getOrientation(
//                rotationMatrix,
//                attitude);
//
//            var angle = Triple((attitude[0] * RAD2DEG), (attitude[1] * RAD2DEG), (attitude[2] * RAD2DEG))
//            Log.d("angle" , angle.toString())
//        }
    }
//
    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
//
    }
}
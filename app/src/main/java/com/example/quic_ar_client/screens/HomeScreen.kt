package com.example.quic_ar_client.screens

import android.util.Log
import androidx.compose.runtime.Composable
import quic.Quic

@Composable
fun HomeScreen(sensorData: Triple<Float, Float, Float>) {
    ARSample(sensorData)
}
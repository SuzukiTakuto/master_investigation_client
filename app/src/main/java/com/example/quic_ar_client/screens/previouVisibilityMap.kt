package com.example.quic_ar_client.screens

val previousVisibilityMap = HashMap<String, Boolean>(66).apply {
    for (i in 1..66) {
        put("marker$i", false)
    }
}

val insightTimeMap = HashMap<String, Long>(66).apply {
    for (i in 1..66) {
        put("marker$i", 0)
    }
}
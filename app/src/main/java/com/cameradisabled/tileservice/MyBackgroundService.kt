package com.cameradisabled.tileservice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class MyBackgroundService : Service() {
    companion object { private const val TAG = "[Test]Service" }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service. onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service. onStartCommand")
        // Do nothing
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service. onDestroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Service. onBind")
        return null
    }
}

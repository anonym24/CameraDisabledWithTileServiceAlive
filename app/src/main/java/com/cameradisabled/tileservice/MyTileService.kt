package com.cameradisabled.tileservice

import android.service.quicksettings.TileService
import android.util.Log

class MyTileService : TileService() {
    companion object { private const val TAG = "[Test]TileService" }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TileService. onCreate")
    }

    override fun onDestroy() {
        Log.d(TAG, "TileService. onDestroy")
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "TileService. onStartListening")
    }

    override fun onStopListening() {
        Log.d(TAG, "TileService. onStopListening")
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "TileService. onClick")
    }
}

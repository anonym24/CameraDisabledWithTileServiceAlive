https://stackoverflow.com/questions/79668330/android-tileservice-quicksettings-causes-error-camera-disabled-when-closing-th

So I noticed a strange bug on Android: when you have an activity with a camera preview and a Quick Settings button (which either does something unrelated to the camera or nothing at all, like in the sample I'm providing), the following issue can occur:

If the `TileService` is still alive (i.e., its `onDestroy` hasn't been called yet), and you close or hide your activity (`onStop` is called) using the back or home button, the `CameraDevice.StateCallback` may trigger the `onError` callback with `ERROR_CAMERA_DISABLED` (`3`).

Why does this happen? If there's even an explanation. I'm not sure if anyone has reported it on the Google Issue Tracker — I tried Googling, but found nothing.

Here are the logs that demonstrate the issue:

    01:50:45.787 [Test]TileService   D  onCreate
    01:50:45.799 [Test]TileService   D  onStartListening
    01:50:49.044 [Test]TileService   D  onStopListening
    01:50:53.270 [Test]Activity      D  onCreate
    01:50:53.289 [Test]Activity      D  onStart
    01:50:53.356 [Test]Activity      D  Camera onOpened
    01:50:56.965 [Test]Activity      D  onStop
    01:50:58.745 [Test]Activity      D  onStart
    01:50:58.764 [Test]Activity      D  Camera onOpened
    01:51:00.724 [Test]Activity      D  onStop
    01:51:00.937 [Test]Activity      E  Camera onError: 3
    01:51:19.056 [Test]TileService   D  onDestroy

You will never get `Camera onError: 3` in this sample if the `TileService` is not running (destroyed). You only get this error if the service is alive. You can make it active by opening the notification panel:

[![enter image description here][1]][1]

After some time, it will be destroyed automatically.

Here is the full code to reproduce the issue (make sure to activate the `TileService` by opening the system notification panel, then start the app, and try hiding or closing it — sometimes the error may not fire, so more attempts are needed):

```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.CameraDisabledWithTileServiceAlive"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.CameraDisabledWithTileServiceAlive">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".MyTileService"
            android:exported="true"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>
    </application>

</manifest>
```

```
package com.cameradisabled.tileservice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "[Test]Activity"
    }

    private lateinit var textureView: TextureView
    private lateinit var btnRequest: Button

    private lateinit var cameraManager: CameraManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Permission result: $isGranted")
        if (isGranted) {
            btnRequest.visibility = View.GONE
            openCameraIfReady()
        } else {
            btnRequest.visibility = View.VISIBLE
        }
    }

    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.texture_view)
        btnRequest = findViewById(R.id.btn_request_permission)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        // Show button if permission not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            btnRequest.visibility = View.VISIBLE
        }

        btnRequest.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // TextureView listener
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                openCameraIfReady()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        openCameraIfReady()
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        closeCamera()
        super.onStop()
    }

    private fun openCameraIfReady() {
        if (!textureView.isAvailable) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return

        btnRequest.visibility = View.GONE
        try {
            val cameraId = cameraManager.cameraIdList.first()
            cameraManager.openCamera(cameraId, stateCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "openCamera failed", e)
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            Log.d(TAG, "Camera onOpened")
            cameraDevice = device
            startPreview()
        }

        override fun onDisconnected(device: CameraDevice) {
            Log.d(TAG, "Camera onDisconnected")
            device.close()
            cameraDevice = null
        }

        override fun onError(device: CameraDevice, error: Int) {
            Log.e(TAG, "Camera onError: $error") // ERROR_CAMERA_DISABLED
            device.close()
            cameraDevice = null
        }
    }

    private fun startPreview() {
        val device = cameraDevice ?: return
        val texture = textureView.surfaceTexture ?: return

        texture.setDefaultBufferSize(textureView.width, textureView.height)
        val surface = Surface(texture)

        previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
        }

        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(sess: CameraCaptureSession) {
                session = sess
                sess.setRepeatingRequest(previewRequestBuilder.build(), null, null)
            }
            override fun onConfigureFailed(sess: CameraCaptureSession) {
                Log.e(TAG, "Preview configure failed")
            }
        }, null)
    }

    private fun closeCamera() {
        session?.close()
        session = null
        cameraDevice?.close()
        cameraDevice = null
    }
}
```

```
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextureView
        android:id="@+id/texture_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <Button
        android:id="@+id/btn_request_permission"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Grant Camera Permission"
        android:layout_gravity="center"
        android:visibility="gone"/>
</FrameLayout>
```

```
package com.cameradisabled.tileservice

import android.service.quicksettings.TileService
import android.util.Log

class MyTileService : TileService() {
    companion object { private const val TAG = "[Test]TileService" }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
    }

    override fun onStopListening() {
        Log.d(TAG, "onStopListening")
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick")
    }
}
```

P.S. I have reproduced this on Android 15 using a Samsung S24 Ultra. More testing on different devices and Android versions is needed.

**Update**:

It seems the Android system might think I'm trying to use the camera in the background simply because the `TileService` background service is running. If that's true, it's a really bad design. For the record, I do stop the camera in the `Activity`’s `onStop()` method, so still why?

**Update 2**:

I have completely removed the `onStart` function in case it could have caused any issues (this is just a sample — I have this issue in real apps too, and they never open the camera in the `onStart` lifecycle state; it's always in the `onResume` lifecycle state).

I tried launching the app from Android Studio and manually (force-killing it and opening it from the home launcher), waited about 10 seconds before closing the app, and I still got the error.


```
03:02:52.144 [Test]Activity     D  onCreate
03:02:52.195 [Test]Activity     D  onSurfaceTextureAvailable RESUMED
03:02:52.224 [Test]Activity     D  Camera onOpened
03:02:57.100 [Test]TileService  D  onCreate
03:02:57.103 [Test]TileService  D  onStartListening
03:02:58.563 [Test]TileService  D  onStopListening
03:03:02.253 [Test]Activity     D  onStop
03:03:02.464 [Test]Activity     E  Camera onError: 3
03:03:28.571 [Test]TileService  D  onDestroy
```

```
override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
    Log.d(TAG, "onSurfaceTextureAvailable ${lifecycle.currentState }")
    openCameraIfReady()
}
```

**Update 3**:

Even if I add `stopRepeating()` and `abortCaptures()` in `onStop()`, it will just cause an exception upon calling `session?.stopRepeating()` because camera was already closed because of camera error 3, and `Camera onError: 3` log even appears earlier then `onStop` log:

```
03:19:21.367 [Test]Activity     D  onCreate
03:19:21.419 [Test]Activity     D  onSurfaceTextureAvailable RESUMED
03:19:21.445 [Test]Activity     D  Camera onOpened
03:19:24.481 [Test]TileService  D  onCreate
03:19:24.488 [Test]Activity     E  Camera onError: 3
03:19:24.678 [Test]TileService  D  onStartListening
03:19:24.725 [Test]Activity     D  onStop
03:19:24.725 AndroidRuntime 	D  Shutting down VM
03:19:24.727 AndroidRuntime 	E  FATAL EXCEPTION: main
Process: com.cameradisabled.tileservice, PID: 11118
java.lang.RuntimeException: Unable to stop activity {com.cameradisabled.tileservice/com.cameradisabled.tileservice.MainActivity}: java.lang.IllegalStateException: CameraDevice was already closed
	at android.app.ActivityThread.callActivityOnStop(ActivityThread.java:6417)
	at android.app.ActivityThread.performStopActivityInner(ActivityThread.java:6383)
	at android.app.ActivityThread.handleStopActivity(ActivityThread.java:6453)
	at android.app.servertransaction.TransactionExecutor.performLifecycleSequence(TransactionExecutor.java:285)
	at android.app.servertransaction.TransactionExecutor.cycleToPath(TransactionExecutor.java:250)
	at android.app.servertransaction.TransactionExecutor.executeLifecycleItem(TransactionExecutor.java:222)
	at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:107)
	at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:81)
	at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2895)
	at android.os.Handler.dispatchMessage(Handler.java:107)
	at android.os.Looper.loopOnce(Looper.java:257)
	at android.os.Looper.loop(Looper.java:342)
	at android.app.ActivityThread.main(ActivityThread.java:9634)
	at java.lang.reflect.Method.invoke(Native Method)
	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:619)
	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:929)
Caused by: java.lang.IllegalStateException: CameraDevice was already closed
	at android.hardware.camera2.impl.CameraDeviceImpl.checkIfCameraClosedOrInError(CameraDeviceImpl.java:2609)
	at android.hardware.camera2.impl.CameraDeviceImpl.stopRepeating(CameraDeviceImpl.java:1469)
	at android.hardware.camera2.impl.CameraCaptureSessionImpl.stopRepeating(CameraCaptureSessionImpl.java:424)
	at com.cameradisabled.tileservice.MainActivity.closeCamera(MainActivity.kt:160)
	at com.cameradisabled.tileservice.MainActivity.onStop(MainActivity.kt:98)
	at android.app.Instrumentation.callActivityOnStop(Instrumentation.java:1742)
	at android.app.Activity.performStop(Activity.java:9635)
	at android.app.ActivityThread.callActivityOnStop(ActivityThread.java:6409)
	at android.app.ActivityThread.performStopActivityInner(ActivityThread.java:6383) 
	at android.app.ActivityThread.handleStopActivity(ActivityThread.java:6453) 
	at android.app.servertransaction.TransactionExecutor.performLifecycleSequence(TransactionExecutor.java:285) 
	at android.app.servertransaction.TransactionExecutor.cycleToPath(TransactionExecutor.java:250) 
	at android.app.servertransaction.TransactionExecutor.executeLifecycleItem(TransactionExecutor.java:222) 
	at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:107) 
	at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:81) 
	at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2895) 
	at android.os.Handler.dispatchMessage(Handler.java:107) 
	at android.os.Looper.loopOnce(Looper.java:257) 
	at android.os.Looper.loop(Looper.java:342) 
	at android.app.ActivityThread.main(ActivityThread.java:9634) 
	at java.lang.reflect.Method.invoke(Native Method) 
	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:619) 
	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:929) 

```

```
override fun onStop() {
    Log.d(TAG, "onStop")
    closeCamera()
    super.onStop()
}

private fun closeCamera() {
    session?.stopRepeating() // will crash here if TileService is alive
    session?.abortCaptures()
    session?.close()
    session = null
    cameraDevice?.close()
    cameraDevice = null
}
```

**Update 4**:

The issue doesn't happen if I close the camera in `onPause` while `TileService` is still alive:

    /*override fun onStart() {
         super.onStart()
         Log.d(TAG, "Activity. onStart")
         openCameraIfReady()
     }
 
     override fun onStop() {
         Log.d(TAG, "Activity. onStop")
         closeCamera()
         super.onStop()
     }*/

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity. onResume")
        openCameraIfReady()
    }

    override fun onPause() {
        Log.d(TAG, "Activity. onPause")
        closeCamera()
        super.onPause()
    }

But this is not a solution.

**Update 5**

If I remove `TileService` and start my own background service then I can't reproduce this issue, so it's definitely related to `TileService`:

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

Manifest:
```
<service
    android:name=".MyBackgroundService"
    android:exported="false" />

<!--<service
    android:name=".MyTileService"
    android:exported="true"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>-->
```

```
class MainActivity : AppCompatActivity() {
    ...

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ...

        // Start test background service
        Intent(this, MyBackgroundService::class.java).also { intent ->
            startService(intent)
            Log.d(TAG, "MyBackgroundService startService called")
        }
```

Logs:

    16:03:32.437 [Test]Activity   D  Activity. onCreate
    16:03:32.455 [Test]Activity   D  MyBackgroundService startService called
    16:03:32.457 [Test]Activity   D  Activity. onStart
    16:03:32.493 [Test]Activity   D  Activity. onSurfaceTextureAvailable, lifecycle state: RESUMED
    16:03:32.493 [Test]Activity   D  Activity. Camera try open, lifecycle state: RESUMED
    16:03:32.516 [Test]Service    D  Service. onCreate
    16:03:32.516 [Test]Service    D  Service. onStartCommand
    16:03:32.524 [Test]Activity   D  Activity. Camera onOpened, lifecycle state: RESUMED
    16:03:35.280 [Test]Activity   D  Activity. onStop

**Update 6**

I was able to reproduce this issue on the following real Samsung devices running Android 15:
- Samsung S24 Ultra (Android 15)
- Samsung S25 Ultra (Android 15)
  
I was not able to reproduce the issue on the following devices and Android versions:
- Samsung S24 Ultra (Android 14)
- Google Pixel 9 Pro (Android 15)
  
I also couldn’t reproduce the issue on Pixel emulators (Android 15).

So, this might be a Samsung specific issue on Android 15. I haven’t tested devices from other manufacturers yet.

At this point, Samsung needs to investigate the issue and provide a hotfix.

I believe it would be easier for the Google team to report this to Samsung, as Google and Samsung are partners.

  [1]: https://i.sstatic.net/65lqbhUB.png

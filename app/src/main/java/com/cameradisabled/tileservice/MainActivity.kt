package com.cameradisabled.tileservice

import android.Manifest
import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// https://stackoverflow.com/questions/79668330/android-tileservice-quicksettings-causes-error-camera-disabled-when-closing-th
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
        Log.d(TAG, "Activity. Permission result: $isGranted")
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
        Log.d(TAG, "Activity. onCreate")
        setContentView(R.layout.activity_main)

        // This background service doesn't cause any issues like the TileService does.
        Intent(this, MyBackgroundService::class.java).also { intent ->
            startService(intent)
            Log.d(TAG, "Activity. startService my custom background service")
        }

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
                Log.d(
                    TAG,
                    "Activity. onSurfaceTextureAvailable, lifecycle state: ${lifecycle.currentState}"
                )
                openCameraIfReady()
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    override fun onStart() {
         super.onStart()
         Log.d(TAG, "Activity. onStart")
         openCameraIfReady()
     }

    /**
     * If the TileService is still alive (i.e., its onDestroy hasn't been called yet),
     * and you close or hide your activity (onStop is called) using the back or home button,
     * the CameraDevice.StateCallback may trigger the onError callback with ERROR_CAMERA_DISABLED (3).
     *
     * You will never get Camera onError: 3 in this sample if the TileService is not running (destroyed).
     * You only get this error if the service is alive. You can make it active by opening the notification panel:
     */
     override fun onStop() {
         Log.d(TAG, "Activity. onStop")
         closeCamera()
         super.onStop()
     }

    /*override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity. onResume")
        openCameraIfReady()
    }

    // if close the camera in onPause before onStop event, then camera error 3 isn't triggered
    override fun onPause() {
        Log.d(TAG, "Activity. onPause")
        closeCamera()
        super.onPause()
    }*/

    private fun openCameraIfReady() {
        if (!textureView.isAvailable) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return

        btnRequest.visibility = View.GONE

        Log.d(TAG, "Activity. Camera try open, lifecycle state: ${lifecycle.currentState}")

        try {
            val cameraId = cameraManager.cameraIdList.first()
            cameraManager.openCamera(cameraId, stateCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Activity. openCamera failed", e)
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            Log.d(TAG, "Activity. Camera onOpened, lifecycle state: ${lifecycle.currentState}")
            cameraDevice = device
            startPreview()
        }

        override fun onDisconnected(device: CameraDevice) {
            Log.d(
                TAG,
                "Activity. Camera onDisconnected, lifecycle state: ${lifecycle.currentState}"
            )
            device.close()
            cameraDevice = null
        }

        override fun onError(device: CameraDevice, error: Int) {
            // ERROR_CAMERA_DISABLED
            Log.e(
                TAG,
                "Activity. Camera onError: $error, lifecycle state: ${lifecycle.currentState}"
            )
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
                Log.e(TAG, "Activity. Preview configure failed")
            }
        }, null)
    }

    private fun closeCamera() {
        try {
            session?.stopRepeating()
            session?.abortCaptures()
            session?.close()
            session = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Throwable) {
            Log.e(TAG, "Activity. closeCamera error: ${e.message ?: e.toString()}")
            e.printStackTrace()
        }
    }
}

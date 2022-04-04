package com.example.androlo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_detection.*
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Detector : AppCompatActivity(), ImageAnalysis.Analyzer {
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        init {
            System.loadLibrary("androlo")
        }
    }

    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var detectorAddr = 0L
    private lateinit var nv21: ByteArray
    private val _paint = Paint()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection)
        if (REQUIRED_PERMISSIONS.all {ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED}) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            startCamera()
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        _paint.color = Color.GREEN
        _paint.style = Paint.Style.STROKE
        _paint.strokeWidth = 2f
        _paint.textSize = 40f
        _paint.textAlign = Paint.Align.LEFT
        surfaceView.setZOrderOnTop(true)
        surfaceView.holder.setFormat(PixelFormat.TRANSPARENT)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val rotation = viewFinder.display.rotation
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build()
                .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(768, 1024))
                .setTargetRotation(rotation)
                .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, this) }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("Detector", "use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    override fun analyze(image: ImageProxy) {
        if (detectorAddr == 0L) {
            detectorAddr = initDetector(this.assets)
        }
        val rotation = image.imageInfo.rotationDegrees
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        if (!::nv21.isInitialized) {
            nv21 = ByteArray(ySize + uSize + vSize)
        }
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val res = detect(detectorAddr, nv21, image.width, image.height, rotation)
        val canvas = surfaceView.holder.lockCanvas()
        if (canvas != null) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)
            for (i in 0 until res[0].toInt()) {
                this.drawDetection(canvas, image.width, image.height, rotation, res, i)
            }
            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
        image.close()
    }

    private fun drawDetection(
        canvas: Canvas,
        frameWidth: Int,
        frameHeight: Int,
        rotation: Int,
        detectionsArr: FloatArray,
        detectionIdx: Int) {
        val pos = detectionIdx * 6 + 1
        val score = detectionsArr[pos + 0]
        val classId = detectionsArr[pos + 1]
        var xmin = detectionsArr[pos + 2]
        var ymin = detectionsArr[pos + 3]
        var xmax = detectionsArr[pos + 4]
        var ymax = detectionsArr[pos + 5]
        if (score == 0.0f) return
        Snackbar.make(viewFinder, "Emergency Vehicle Detected", Snackbar.LENGTH_SHORT).show()
        val w = if (rotation == 0 || rotation == 180) frameWidth else frameHeight
        val h = if (rotation == 0 || rotation == 180) frameHeight else frameWidth
        val scaleX = viewFinder.width.toFloat()/w
        val scaleY = viewFinder.height.toFloat()/h
        val xoff = 0
        val yoff = 0
        xmin = xoff + xmin * scaleX
        xmax = xoff + xmax * scaleX
        ymin = xoff + ymin * scaleY
        ymax = yoff + ymax * scaleY
        val p = Path()
        p.moveTo(xmin, ymin)
        p.lineTo(xmax, ymin)
        p.lineTo(xmax, ymax)
        p.lineTo(xmin, ymax)
        p.lineTo(xmin, ymin)
        canvas.drawPath(p, _paint)
        val sc = "%.2f".format(score)
        canvas.drawText(sc, xmin, ymin, _paint)
    }

    private external fun initDetector(assetManager: AssetManager?): Long
    private external fun destroyDetector(ptr: Long)
    private external fun detect(ptr: Long, srcAddr: ByteArray, width: Int, height: Int, rotation: Int): FloatArray

    fun buttonStopDetectionClicked(view: View) {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
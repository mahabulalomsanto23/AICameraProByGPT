package com.aicamerapro

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.aicamerapro.processing.EnhanceMode
import com.aicamerapro.processing.EnhanceSettings
import com.aicamerapro.processing.VividProcessor
import com.aicamerapro.processing.PortraitProcessor
import com.aicamerapro.processing.SceneAnalyzer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var preview: PreviewView
    private lateinit var photo: ImageView
    private lateinit var capture: ImageCapture
    private lateinit var status: TextView
    private lateinit var intensityLabel: TextView
    private lateinit var intensityTitle: TextView
    private lateinit var modeRow: LinearLayout
    private lateinit var blurPresetButton: TextView
    private lateinit var flashButton: TextView
    private var original: Bitmap? = null
    private var enhanced: Bitmap? = null
    private var currentMode = EnhanceMode.VIVID
    private var intensity = 0.72f
    private var showingOriginal = false
    private var cameraBound = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashEnabled = false
    private var saveMode = "both"
    private var startupMode = "VIVID"
    private var defaultPortraitBlur = 0.50f
    private var sourceExifUri: Uri? = null
    private var sourceExifFile: File? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else status.text = "Camera permission is needed to take photos"
    }
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { loadPhoto(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 11, 24)
        window.navigationBarColor = Color.rgb(9, 11, 24)
        buildInterface()
        lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) {
                listOf(
                    CameraSettings.readSaveMode(this@MainActivity),
                    CameraSettings.readStartupMode(this@MainActivity),
                    CameraSettings.readPortraitBlur(this@MainActivity).toString(),
                    CameraSettings.readFlashEnabled(this@MainActivity).toString()
                )
            }
            saveMode = stored[0]
            startupMode = stored[1]
            currentMode = runCatching { EnhanceMode.valueOf(startupMode) }.getOrDefault(EnhanceMode.VIVID)
            defaultPortraitBlur = stored[2].toFloatOrNull()?.coerceIn(0.25f, 1f) ?: 0.50f
            intensity = if (currentMode == EnhanceMode.PORTRAIT) defaultPortraitBlur else 0.72f
            flashEnabled = stored[3].toBoolean()
            flashButton.text = if (flashEnabled) "⚡" else "⚡"
            intensityTitle.text = if (currentMode == EnhanceMode.PORTRAIT) "BACKGROUND BLUR" else "VIBRANCE POWER"
            intensityLabel.text = "${(intensity * 100).toInt()}%"
            refreshModes()
            if (cameraProvider != null) bindCamera()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gradient(a: Int, b: Int, radius: Int = 24) =
        android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply { cornerRadius = dp(radius).toFloat() }
    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD) }

    private fun buildInterface() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            setBackgroundColor(Color.BLACK)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(label("AI Camera", 18f, Color.WHITE, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        flashButton = action(if (flashEnabled) "⚡" else "⚡", 0xFF252525.toInt()) { toggleFlash() }
        top.addView(flashButton, LinearLayout.LayoutParams(dp(44), dp(40)).apply { marginEnd = dp(8) })
        top.addView(action("⚙", 0xFF252525.toInt()) { showSaveSettings() }, LinearLayout.LayoutParams(dp(44), dp(40)))
        root.addView(top)

        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            clipToOutline = true
        }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        preview.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                boundCamera?.let { camera ->
                    val point = preview.meteringPointFactory.createPoint(event.x, event.y)
                    camera.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    )
                }
                true
            } else true
        }
        photo = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        frame.addView(photo, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(6)
            bottomMargin = dp(10)
        })

        status = label("Ready", 12f, 0xFFBDBDBD.toInt())
        status.gravity = Gravity.CENTER
        root.addView(status, LinearLayout.LayoutParams(-1, dp(28)))

        modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addMode("AI", EnhanceMode.AI, 0xFF555555.toInt())
        addMode("VIVID+", EnhanceMode.VIVID, 0xFFFF4081.toInt())
        addMode("PORTRAIT", EnhanceMode.PORTRAIT, 0xFF555555.toInt())
        addMode("NATURAL", EnhanceMode.NORMAL, 0xFF555555.toInt())
        root.addView(modeRow, LinearLayout.LayoutParams(-1, dp(46)))
        
        val sliderHead = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        intensityTitle = label("COLOR INTENSITY", 10f, 0xFFBDBDBD.toInt(), true)
        sliderHead.addView(intensityTitle, LinearLayout.LayoutParams(0, -2, 1f))
        intensityLabel = label("${(intensity * 100).toInt()}%", 11f, Color.WHITE, true)
        sliderHead.addView(intensityLabel)
        root.addView(sliderHead)
        val slider = SeekBar(this).apply {
            max = 100
            progress = (intensity * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFFF4081.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                    intensity = value / 100f
                    intensityLabel.text = "$value%"
                    if (fromUser) original?.let { process(it) }
                }
                override fun onStartTrackingTouch(seek: SeekBar?) {}
                override fun onStopTrackingTouch(seek: SeekBar?) {}
            })
        }
        root.addView(slider, LinearLayout.LayoutParams(-1, dp(34)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }
        actions.addView(action("Gallery", 0xFF292929.toInt()) { picker.launch("image/*") },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(12) })
        actions.addView(action("●", 0xFFFF4081.toInt()) { takePhoto() },
            LinearLayout.LayoutParams(dp(68), dp(60)))
        actions.addView(action("Flip", 0xFF292929.toInt()) { switchCamera() },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(12) })
        root.addView(actions)

        val photoActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        photoActions.addView(action("Retake", 0xFF292929.toInt()) {
            photo.visibility = View.GONE
            preview.visibility = View.VISIBLE
            original = null
            enhanced = null
            status.text = "Ready"
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        photoActions.addView(action("Save", 0xFFFF4081.toInt()) { savePhoto() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        root.addView(photoActions)
        blurPresetButton = action("Portrait blur presets", 0xFF292929.toInt()) { showBlurPresets() }
        blurPresetButton.visibility = View.GONE

        setContentView(root)
        refreshModes()
    }

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1,dp(height)) }
    private fun action(text: String,color: Int,click: () -> Unit) = label(text,12f,Color.WHITE,true).apply {
        gravity=Gravity.CENTER; background=gradient(color,0xFF232546.toInt(),16); setOnClickListener { click() }
    }
    private fun addMode(title: String,mode: EnhanceMode,color: Int) {
        val view=label(title,9f,Color.WHITE,true).apply {
            gravity=Gravity.CENTER; setPadding(dp(5),dp(12),dp(5),dp(12)); background=gradient(color,0xFF20233C.toInt(),12)
            setOnClickListener { currentMode=mode; intensityTitle.text=if(mode==EnhanceMode.PORTRAIT) "BACKGROUND BLUR" else "VIBRANCE POWER"; showingOriginal=false; refreshModes(); original?.let { process(it) } }
        }
        modeRow.addView(view,LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(2),dp(8),dp(2),0) })
    }
    private fun refreshModes() {
        for (i in 0 until modeRow.childCount) {
            val active=(i==0 && currentMode==EnhanceMode.AI)||(i==1 && currentMode==EnhanceMode.VIVID)||(i==2 && currentMode==EnhanceMode.PORTRAIT)||(i==3 && currentMode==EnhanceMode.NORMAL)
            modeRow.getChildAt(i).alpha=if(active) 1f else 0.48f
        }
        if (::blurPresetButton.isInitialized) blurPresetButton.visibility = if (currentMode == EnhanceMode.PORTRAIT) View.VISIBLE else View.GONE
    }
    private fun buildCameraAdjustments(root: LinearLayout) {
        fun adjustmentRow(title: String, initial: Int, onChange: (Int) -> Unit) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(title,9f,0xFFBFB7DC.toInt(),true), LinearLayout.LayoutParams(dp(82),-2))
            val seek = SeekBar(this).apply {
                max = 100; progress = initial
                progressTintList = android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt())
                thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFF4FA3.toInt())
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            row.addView(seek, LinearLayout.LayoutParams(0,dp(32),1f))
            root.addView(row)
        }
        adjustmentRow("EXPOSURE",50) { value ->
            val camera = boundCamera ?: return@adjustmentRow
            val range = camera.cameraInfo.exposureState.exposureCompensationRange
            val index = range.lower + ((range.upper - range.lower) * value / 100f).toInt()
            camera.cameraControl.setExposureCompensationIndex(index)
            status.text = "Exposure compensation: $index"
        }
        adjustmentRow("ZOOM",0) { value ->
            val camera = boundCamera ?: return@adjustmentRow
            val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            val ratio = 1f + (maxZoom - 1f) * value / 100f
            camera.cameraControl.setZoomRatio(ratio)
            status.text = "Camera zoom: %.1fx".format(Locale.US, ratio)
        }
        root.addView(space(4))
    }

    private fun syncCameraControls() {
        boundCamera?.cameraControl?.setExposureCompensationIndex(0)
        boundCamera?.cameraControl?.setZoomRatio(1f)
    }

    private fun showBlurPresets() {
        val names = arrayOf("Natural  •  25%", "Medium  •  50%", "Strong  •  75%", "Maximum  •  100%")
        val values = floatArrayOf(0.25f, 0.50f, 0.75f, 1.0f)
        AlertDialog.Builder(this)
            .setTitle("Portrait background blur")
            .setItems(names) { dialog, which ->
                currentMode = EnhanceMode.PORTRAIT
                intensity = values[which]
                intensityTitle.text = "BACKGROUND BLUR"
                intensityLabel.text = "${(intensity * 100).toInt()}%"
                refreshModes()
                original?.let { process(it) }
                dialog.dismiss()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun startCamera() {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        },ContextCompat.getMainExecutor(this))
    }
    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraPreview = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
        capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(if (flashEnabled && lensFacing == CameraSelector.LENS_FACING_BACK) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
            .build()
        try {
            provider.unbindAll()
            boundCamera = provider.bindToLifecycle(this, selector, cameraPreview, capture)
            cameraBound = true
            syncCameraControls()
            status.text = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Rear camera ready • AI mode" else "Front camera ready • AI mode"
        } catch (e: Exception) {
            cameraBound = false
            status.text = "Camera unavailable: " + e.localizedMessage
        }
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        if (cameraProvider != null) bindCamera()
    }

    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        flashButton.text = if (flashEnabled) "⚡ FLASH ON" else "⚡ FLASH OFF"
        lifecycleScope.launch { withContext(Dispatchers.IO) { CameraSettings.writeFlashEnabled(this@MainActivity, flashEnabled) } }
        if (cameraProvider != null && lensFacing == CameraSelector.LENS_FACING_BACK) bindCamera()
        status.text = if (flashEnabled) "Flash enabled for rear camera" else "Flash disabled"
    }

    private fun takePhoto() {
        if(!cameraBound) { status.text="Camera is starting…"; return }
        status.text="Capturing full-resolution photo…"
        val file=File(cacheDir,"capture_"+System.currentTimeMillis()+".jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(this),object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                val bitmap=BitmapFactory.decodeFile(file.absolutePath)
                if(bitmap!=null) { sourceExifFile = file; sourceExifUri = null; showPhoto(bitmap) } else status.text="Could not read captured image"
            }
            override fun onError(exception: ImageCaptureException) { status.text="Capture failed: "+exception.message }
        })
    }
    private fun loadPhoto(uri: Uri) {
        worker.execute {
            val bitmap=contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            runOnUiThread { if(bitmap!=null) { sourceExifUri = uri; sourceExifFile = null; showPhoto(bitmap) } else status.text="Could not open this photo" }
        }
    }
    private fun showPhoto(bitmap: Bitmap) { original=bitmap; enhanced=null; photo.setImageBitmap(bitmap); photo.visibility=View.VISIBLE; preview.visibility=View.GONE; showingOriginal=false; process(bitmap) }
    private fun process(bitmap: Bitmap) {
        status.text="Enhancing colors • "+currentMode.name+"…"
        val settings=EnhanceSettings(mode=currentMode,intensity=intensity,warmth=0.10f,contrast=1.10f+intensity*0.12f)
        if (currentMode == EnhanceMode.PORTRAIT) {
            status.text = "AI subject detection • Portrait blur…"
            PortraitProcessor.enhance(bitmap, settings, intensity,
                { result ->
                    runOnUiThread {
                        enhanced = result
                        photo.setImageBitmap(result)
                        showingOriginal = false
                        status.text = "✦ Portrait enhanced • Offline AI segmentation"
                    }
                },
                { error ->
                    runOnUiThread {
                        status.text = "Portrait model unavailable • Using safe AI enhancement"
                        worker.execute {
                            val fallback = VividProcessor.enhance(bitmap, settings.copy(mode = EnhanceMode.AI))
                            runOnUiThread { enhanced = fallback; photo.setImageBitmap(fallback) }
                        }
                    }
                }
            )
        } else if (currentMode == EnhanceMode.AI) {
            status.text = "Offline AI • Analyzing scene…"
            SceneAnalyzer.analyze(bitmap,
                { profile ->
                    worker.execute {
                        val result = VividProcessor.enhance(bitmap, SceneAnalyzer.tune(settings, profile))
                        runOnUiThread {
                            enhanced = result
                            photo.setImageBitmap(result)
                            showingOriginal = false
                            status.text = "✦ AI scene: " + profile.name.lowercase().replaceFirstChar { it.uppercase() } + " • Adaptive enhancement"
                        }
                    }
                },
                {
                    worker.execute {
                        val result = VividProcessor.enhance(bitmap, settings)
                        runOnUiThread { enhanced = result; photo.setImageBitmap(result); showingOriginal = false; status.text = "✦ AI fallback enhancement • Offline" }
                    }
                }
            )
        } else {
            worker.execute {
                val result=VividProcessor.enhance(bitmap,settings)
                runOnUiThread { enhanced=result; photo.setImageBitmap(result); showingOriginal=false; status.text="✦ Enhanced • "+result.width+" × "+result.height+" • On-device" }
            }
        }
    }
    private fun showSaveSettings() {
        val choices = arrayOf("Image saving preference", "Default startup mode", "Default portrait blur", "Flash on launch")
        AlertDialog.Builder(this).setTitle("Camera settings")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> showSaveModeSetting()
                    1 -> showStartupModeSetting()
                    2 -> showDefaultBlurSetting()
                    3 -> showFlashPreferenceSetting()
                }
            }.setNegativeButton("CLOSE", null).show()
    }

    private fun showSaveModeSetting() {
        val choices = arrayOf("Original + Enhanced", "Enhanced Only")
        AlertDialog.Builder(this).setTitle("Image saving preference")
            .setSingleChoiceItems(choices, if (saveMode == "both") 0 else 1) { dialog, which ->
                val selected = if (which == 0) "both" else "enhanced"
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { CameraSettings.writeSaveMode(this@MainActivity, selected) }
                    saveMode = selected
                    status.text = "Save preference: " + choices[which]
                }
                dialog.dismiss()
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun showStartupModeSetting() {
        val modes = arrayOf("AI", "VIVID", "NORMAL", "PORTRAIT")
        AlertDialog.Builder(this).setTitle("Default startup mode")
            .setSingleChoiceItems(modes, modes.indexOf(startupMode).coerceAtLeast(0)) { dialog, which ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { CameraSettings.writeStartupMode(this@MainActivity, modes[which]) }
                    startupMode = modes[which]
                    status.text = "Startup mode saved: " + modes[which]
                }
                dialog.dismiss()
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun showDefaultBlurSetting() {
        val labels = arrayOf("Natural", "Medium", "Strong", "Maximum")
        val values = floatArrayOf(0.25f, 0.50f, 0.75f, 1f)
        val selected = values.indices.minByOrNull { kotlin.math.abs(values[it] - defaultPortraitBlur) } ?: 1
        AlertDialog.Builder(this).setTitle("Default portrait blur")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { CameraSettings.writePortraitBlur(this@MainActivity, values[which]) }
                    defaultPortraitBlur = values[which]
                    intensity = values[which]
                    intensityLabel.text = "${(intensity * 100).toInt()}%"
                    status.text = "Portrait blur preference saved"
                }
                dialog.dismiss()
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun showFlashPreferenceSetting() {
        val choices = arrayOf("Flash off", "Flash on")
        AlertDialog.Builder(this).setTitle("Flash on launch")
            .setSingleChoiceItems(choices, if (flashEnabled) 1 else 0) { dialog, which ->
                flashEnabled = which == 1
                flashButton.text = if (flashEnabled) "⚡ FLASH ON" else "⚡ FLASH OFF"
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { CameraSettings.writeFlashEnabled(this@MainActivity, flashEnabled) }
                    if (cameraProvider != null) bindCamera()
                    status.text = "Flash preference saved"
                }
                dialog.dismiss()
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun copySourceExifTo(target: File) {
        val source = try {
            sourceExifFile?.takeIf { it.exists() }?.let { ExifInterface(it.absolutePath) }
                ?: sourceExifUri?.let { uri -> contentResolver.openInputStream(uri)?.use { ExifInterface(it) } }
        } catch (_: Exception) { null } ?: return
        val output = ExifInterface(target.absolutePath)
        val tags = arrayOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH
        )
        tags.forEach { tag -> source.getAttribute(tag)?.let { output.setAttribute(tag, it) } }
        output.saveAttributes()
    }

    private fun writeBitmapToGallery(bitmap: Bitmap, name: String): Boolean {
        val temp = File(cacheDir, name)
        try {
            temp.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, output)) return false
            }
            copySourceExifTo(temp)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Camera Pro")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val ok = try {
                contentResolver.openOutputStream(uri)?.use { output -> temp.inputStream().use { it.copyTo(output) }; true } == true
            } catch (_: Exception) { false }
            if (!ok) contentResolver.delete(uri, null, null)
            return ok
        } catch (_: Exception) {
            return false
        } finally {
            temp.delete()
        }
    }

    private fun savePhoto() {
        val bitmap = enhanced ?: run { status.text = "Capture or open a photo first"; return }
        val originalBitmap = original
        val selectedMode = saveMode
        status.text = "Saving photo…"
        worker.execute {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val enhancedOk = writeBitmapToGallery(bitmap, "AICameraPro_${stamp}_Enhanced.jpg")
            val originalOk = if (selectedMode == "both" && originalBitmap != null) {
                writeBitmapToGallery(originalBitmap, "AICameraPro_${stamp}_Original.jpg")
            } else true
            runOnUiThread {
                status.text = when {
                    enhancedOk && originalOk && selectedMode == "both" -> "Original + Enhanced saved • Pictures / AI Camera Pro"
                    enhancedOk && originalOk -> "Enhanced photo saved • Pictures / AI Camera Pro"
                    else -> "Could not save all selected photos"
                }
            }
        }
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}

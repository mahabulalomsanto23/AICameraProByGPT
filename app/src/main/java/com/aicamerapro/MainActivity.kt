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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.aicamerapro.processing.EnhanceMode
import com.aicamerapro.processing.EnhanceSettings
import com.aicamerapro.processing.VividProcessor
import com.aicamerapro.processing.PortraitProcessor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var preview: PreviewView
    private lateinit var photo: ImageView
    private lateinit var capture: ImageCapture
    private lateinit var status: TextView
    private lateinit var intensityLabel: TextView
    private lateinit var modeRow: LinearLayout
    private var original: Bitmap? = null
    private var enhanced: Bitmap? = null
    private var currentMode = EnhanceMode.AI
    private var intensity = 0.78f
    private var showingOriginal = false
    private var cameraBound = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else status.text = "Camera permission is needed to take photos"
    }
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { loadPhoto(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 11, 24)
        window.navigationBarColor = Color.rgb(9, 11, 24)
        buildInterface()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gradient(a: Int, b: Int, radius: Int = 24) =
        android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply { cornerRadius = dp(radius).toFloat() }
    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD) }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12))
            background = gradient(Color.rgb(9,11,24), Color.rgb(23,17,48), 0)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("✦ AI CAMERA PRO", 21f, Color.WHITE, true))
        brand.addView(label("PROFESSIONAL PHOTOGRAPHY ENGINE", 9f, 0xFFAEA4D6.toInt(), true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("● OFFLINE", 10f, 0xFF4CFFC1.toInt(), true))
        root.addView(header); root.addView(space(10))
        val frame = FrameLayout(this).apply { background = gradient(0xFF241E45.toInt(), 0xFF0C1123.toInt(), 26); clipToOutline = true }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        photo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE; setBackgroundColor(Color.BLACK) }
        frame.addView(preview, FrameLayout.LayoutParams(-1,-1))
        frame.addView(photo, FrameLayout.LayoutParams(-1,-1))
        val hint = label("LIVE CAMERA  •  VIVID AI", 10f, Color.WHITE, true).apply {
            setPadding(dp(12),dp(8),dp(12),dp(8)); background = gradient(0xCC7C4DFF.toInt(),0xCC00B8D4.toInt(),20)
        }
        frame.addView(hint, FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply { setMargins(dp(12),dp(12),0,0) })
        root.addView(frame, LinearLayout.LayoutParams(-1,0,1f)); root.addView(space(12))
        status = label("Ready • AI mode selected",12f,0xFFBFB7DC.toInt()); root.addView(status); root.addView(space(8))
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10)); background = gradient(0xFF211B3E.toInt(),0xFF161C34.toInt(),20) }
        card.addView(label("COLOR SCIENCE",10f,0xFF00E5FF.toInt(),true))
        modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addMode("AI AUTO",EnhanceMode.AI,0xFF8B5CF6.toInt())
        addMode("VIVID+",EnhanceMode.VIVID,0xFFFF4FA3.toInt())
        addMode("PORTRAIT",EnhanceMode.PORTRAIT,0xFF00BCD4.toInt())
        addMode("NORMAL",EnhanceMode.NORMAL,0xFF65708A.toInt())
        card.addView(modeRow); root.addView(card); root.addView(space(10))
        val sliderHead = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sliderHead.addView(label("VIBRANCE POWER",10f,0xFFBFB7DC.toInt(),true),LinearLayout.LayoutParams(0,-2,1f))
        intensityLabel = label("78%",12f,0xFF00E5FF.toInt(),true); sliderHead.addView(intensityLabel); root.addView(sliderHead)
        val slider = SeekBar(this).apply {
            max = 100; progress = 78
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFFF4FA3.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt())
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    intensity = p / 100f; intensityLabel.text = "$p%"
                    if (fromUser) original?.let { process(it) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(slider); root.addView(space(8))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("GALLERY",0xFF303B5B.toInt()) { picker.launch("image/*") },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd=dp(6) })
        actions.addView(action("●  CAPTURE",0xFF8B35E8.toInt()) { takePhoto() },LinearLayout.LayoutParams(0,dp(54),1.35f).apply { marginStart=dp(6); marginEnd=dp(6) })
        actions.addView(action("SAVE",0xFF007F83.toInt()) { savePhoto() },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginStart=dp(6) })
        root.addView(actions); root.addView(space(8))
        root.addView(label("↔  TAP HERE TO COMPARE ORIGINAL / ENHANCED",10f,0xFF00E5FF.toInt(),true).apply {
            gravity=Gravity.CENTER; setPadding(0,dp(8),0,dp(4)); setOnClickListener { toggleCompare() }
        })
        setContentView(root); refreshModes()
    }

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1,dp(height)) }
    private fun action(text: String,color: Int,click: () -> Unit) = label(text,12f,Color.WHITE,true).apply {
        gravity=Gravity.CENTER; background=gradient(color,0xFF232546.toInt(),16); setOnClickListener { click() }
    }
    private fun addMode(title: String,mode: EnhanceMode,color: Int) {
        val view=label(title,9f,Color.WHITE,true).apply {
            gravity=Gravity.CENTER; setPadding(dp(5),dp(12),dp(5),dp(12)); background=gradient(color,0xFF20233C.toInt(),12)
            setOnClickListener { currentMode=mode; showingOriginal=false; refreshModes(); original?.let { process(it) } }
        }
        modeRow.addView(view,LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(2),dp(8),dp(2),0) })
    }
    private fun refreshModes() {
        for (i in 0 until modeRow.childCount) {
            val active=(i==0 && currentMode==EnhanceMode.AI)||(i==1 && currentMode==EnhanceMode.VIVID)||(i==2 && currentMode==EnhanceMode.PORTRAIT)||(i==3 && currentMode==EnhanceMode.NORMAL)
            modeRow.getChildAt(i).alpha=if(active) 1f else 0.48f
        }
    }
    private fun startCamera() {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider=future.get()
            val p=Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            capture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            try { provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,capture); cameraBound=true }
            catch(e: Exception) { status.text="Camera unavailable: "+e.localizedMessage }
        },ContextCompat.getMainExecutor(this))
    }
    private fun takePhoto() {
        if(!cameraBound) { status.text="Camera is starting…"; return }
        status.text="Capturing full-resolution photo…"
        val file=File(cacheDir,"capture_"+System.currentTimeMillis()+".jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(this),object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                val bitmap=BitmapFactory.decodeFile(file.absolutePath)
                if(bitmap!=null) { showPhoto(bitmap); file.delete() } else status.text="Could not read captured image"
            }
            override fun onError(exception: ImageCaptureException) { status.text="Capture failed: "+exception.message }
        })
    }
    private fun loadPhoto(uri: Uri) {
        worker.execute {
            val bitmap=contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            runOnUiThread { if(bitmap!=null) showPhoto(bitmap) else status.text="Could not open this photo" }
        }
    }
    private fun showPhoto(bitmap: Bitmap) { original=bitmap; photo.visibility=View.VISIBLE; preview.visibility=View.GONE; showingOriginal=false; process(bitmap) }
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
        } else {
            worker.execute {
                val result=VividProcessor.enhance(bitmap,settings)
                runOnUiThread { enhanced=result; photo.setImageBitmap(result); showingOriginal=false; status.text="✦ Enhanced • "+result.width+" × "+result.height+" • On-device" }
            }
        }
    }
    private fun toggleCompare() {
        val before=original ?: run { status.text="Capture or open a photo to compare"; return }
        val after=enhanced ?: return
        showingOriginal=!showingOriginal; photo.setImageBitmap(if(showingOriginal) before else after)
        status.text=if(showingOriginal) "NORMAL • Tap compare to see VIVID result" else "ENHANCED • Tap compare to see ORIGINAL"
    }
    private fun savePhoto() {
        val bitmap=enhanced ?: run { status.text="Capture or open a photo first"; return }
        worker.execute {
            val name="AICameraPro_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())+".jpg"
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,name)
                put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AI Camera Pro")
            }
            val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
            val ok=uri?.let { contentResolver.openOutputStream(it)?.use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG,96,stream) } } ?: false
            runOnUiThread { status.text=if(ok) "Saved to Pictures / AI Camera Pro" else "Could not save photo" }
        }
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}

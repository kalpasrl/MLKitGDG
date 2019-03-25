package com.keridano.mlkit.custommodels

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.keridano.mlkit.R
import com.theartofdev.edmodo.cropper.CropImage
import kotlinx.android.synthetic.main.activity_custom_models.*
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@Suppress("unused")
class CustomModelsActivity : AppCompatActivity() {

    companion object {
        const val DETECTION_THRESHOLD = 0.6
        const val NUM_BOXES_PER_BLOCK = 5
        const val NUM_CLASSES = 5
        private const val IMAGE_MEAN = 1
        private const val IMAGE_STD = 255.0f

        private val ANCHORS = doubleArrayOf(0.57273, 0.677385, 1.87446, 2.06253, 3.33843, 5.47434, 7.88282, 3.52778, 9.77052, 9.16828)
    }

    private val outputData = arrayOf(
            "Squirtle",
            "Bulbasaur",
            "Charmander",
            "Pikachu",
            "Yoda")

    private val imageView by lazy { findViewById<ImageView>(R.id.custom_models_image_view)!! }
    private val bottomSheetButton by lazy { findViewById<FrameLayout>(R.id.bottom_sheet_button)!! }
    private val bottomSheetRecyclerView by lazy { findViewById<RecyclerView>(R.id.bottom_sheet_recycler_view)!! }
    private val bottomSheetBehavior by lazy { BottomSheetBehavior.from(findViewById(R.id.bottom_sheet)!!) }
    private val customModelsModels = ArrayList<CustomModelsModel>()

    private var imgData: ByteBuffer = ByteBuffer.allocateDirect(
            1       // DIM_BATCH_SIZE
                    * 416   // Input image width
                    * 416   // Input image height
                    * 3     // Pixel size
                    * 4)    // Bytes per channel
    private val intValues = IntArray(416 * 416)
    private val output: Array<Array<Array<FloatArray>>> =
            Array(1) {
                Array(13)
                {
                    Array(13)
                    {
                        FloatArray(50)
                    }
                }
            }

    init {
        imgData.order(ByteOrder.nativeOrder())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_models)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bottomSheetButton.setOnClickListener {
            CropImage.activity().start(this)
        }

        bottomSheetRecyclerView.layoutManager = LinearLayoutManager(this)
        bottomSheetRecyclerView.adapter = CustomModelsAdapter(this, customModelsModels)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result = CropImage.getActivityResult(data)

            if (resultCode == Activity.RESULT_OK) {
                val imageUri = result.uri
                analyzeImage(MediaStore.Images.Media.getBitmap(contentResolver, imageUri))
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(this, "There was an error in cutting the image: ${result.error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        imgData.rewind()
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 416, 416, true)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
        // floating point.
        var pixel = 0
        for (i in 0 until 416) {
            for (j in 0 until 416) {
                val `val` = intValues[pixel++]
                addPixelValue(`val`)
            }
        }
    }

    private fun addPixelValue(pixelValue: Int) {
        imgData.putFloat((((pixelValue shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        imgData.putFloat((((pixelValue shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
    }

    private fun analyzeImage(image: Bitmap?) {
        if (image == null) {
            Toast.makeText(this, "There was an error in gathering the image", Toast.LENGTH_SHORT).show()
            return
        }

        imageView.setImageBitmap(null)
        customModelsModels.clear()
        bottomSheetRecyclerView.adapter?.notifyDataSetChanged()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        showProgress()

//        // Configure a local model source
//        val localSource = FirebaseLocalModelSource.Builder("pokemon")
//                .setAssetFilePath("yolov2-tiny-kpoke.tflite")
//                .build()
//        FirebaseModelManager.getInstance().registerLocalModelSource(localSource)
//
//        // Create an interpreter from your model sources
//        val options = FirebaseModelOptions.Builder()
//                .setLocalModelName("yolov2-tiny-kpoke.tflite")
//                .build()
//        val interpreter = FirebaseModelInterpreter.getInstance(options)
//
//        // Specify the model's input and output
//        val inputOutputOptions = FirebaseModelInputOutputOptions.Builder()
//                .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 416, 416, 3, 4))
//                .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 13, 13, 285))
//                .build()
//        val inputs = FirebaseModelInputs.Builder()
//                .add(imgData)
//                .build()

        // Perform inference on input data
        convertBitmapToByteBuffer(image)

        val interpreter = Interpreter(fromAsset("yolov2-tiny-kpoke.tflite"), Interpreter.Options())
        interpreter.run(imgData, output)

        val outputMap = processOutput(output)

        if(outputMap.isEmpty()){
            imageView.setImageBitmap(image)
            Toast.makeText(this, "No Pokemon found in image", Toast.LENGTH_LONG).show()
            hideProgress()
        }else{
            for ((key, value) in outputMap) {
                if (value > 0.5)
                    customModelsModels.add(CustomModelsModel(outputData[key], value))
                    customModelsModels.sortWith(Comparator { o1, o2 ->
                    when {
                        o1.confidence == o2.confidence -> 0
                        o1.confidence < o2.confidence -> 1
                        else -> -1
                    }
                })
            }

            imageView.setImageBitmap(image)
            hideProgress()
            bottomSheetRecyclerView.adapter?.notifyDataSetChanged()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun fromAsset(path: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun Array<FloatArray>.flatten(): List<Float> {
        val retList = mutableListOf<Float>()
        this.forEach {
            retList.addAll(it.asIterable())
        }
        return retList
    }

    private fun processOutput(data: Array<Array<Array<FloatArray>>>): Map<Int, Float> {
        //  https://hackernoon.com/understanding-yolo-f5a74bbc7967

        val output = data[0] //output is now 13x13x285
        val flatOutput = mutableListOf<Float>()
        output.forEach { flatOutput.addAll(it.flatten()) }

        val resultsMap = mutableMapOf<Int, Float>()

        val gridHeight = 13
        val gridWidth = 13

        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {
                for (b in 0 until NUM_BOXES_PER_BLOCK) {
                    val offset = (gridWidth * (NUM_BOXES_PER_BLOCK * (NUM_CLASSES + 5)) * y
                            + NUM_BOXES_PER_BLOCK * (NUM_CLASSES + 5) * x
                            + (NUM_CLASSES + 5) * b)
//                    val blockSize = 32f  // bounding rect in calcolo e la verifica la lascio ai posteri (cioè tu Enrico)
//
//                    val xPos = (x + sigmoid(flatOutput[offset + 0])) * blockSize
//                    val yPos = (y + sigmoid(flatOutput[offset + 1])) * blockSize
//
//                    val w = (Math.exp(flatOutput[offset + 2].toDouble()) * ANCHORS[2 * b + 0]).toFloat() * blockSize
//                    val h = (Math.exp(flatOutput[offset + 3].toDouble()) * ANCHORS[2 * b + 1]).toFloat() * blockSize
//
//                    val rect = RectF(
//                            Math.max(0f, xPos - w / 2),
//                            Math.max(0f, yPos - h / 2),
//                            Math.min((416 - 1).toFloat(), xPos + w / 2),
//                            Math.min((416 - 1).toFloat(), yPos + h / 2))
                    val confidence = sigmoid(flatOutput[offset + 4])

                    var detectedClass = -1
                    var maxClass = 0f

                    val classes = FloatArray(NUM_CLASSES)
                    for (c in 0 until NUM_CLASSES) {
                        classes[c] = flatOutput[offset + 5 + c]
                    }
                    softmax(classes)

                    for (c in 0 until NUM_CLASSES) {
                        if (classes[c] > maxClass) {
                            detectedClass = c
                            maxClass = classes[c]
                        }
                    }

                    val confidenceInClass = maxClass * confidence
                    if (confidenceInClass > DETECTION_THRESHOLD) {
                        if (detectedClass in resultsMap){
                            resultsMap[detectedClass] = Math.max(resultsMap.get(detectedClass) as Float, confidenceInClass)
                        }else {
                            resultsMap[detectedClass] = confidenceInClass
                        }
                    }
                }
            }
        }
        return resultsMap
    }

    private fun sigmoid(x: Float): Float {
        return (1.0 / (1.0 + Math.exp((-x).toDouble()))).toFloat()
    }

    private fun softmax(floats: FloatArray) {
        var max = java.lang.Float.NEGATIVE_INFINITY
        for (number in floats) {
            max = Math.max(max, number)
        }
        var sum = 0.0f
        for (i in floats.indices) {
            floats[i] = Math.exp((floats[i] - max).toDouble()).toFloat()
            sum += floats[i]
        }
        for (i in floats.indices) {
            floats[i] = floats[i] / sum
        }
    }

    private fun showProgress() {
        findViewById<View>(R.id.bottom_sheet_button_image).visibility = View.GONE
        findViewById<View>(R.id.bottom_sheet_button_progress).visibility = View.VISIBLE
    }

    private fun hideProgress() {
        findViewById<View>(R.id.bottom_sheet_button_image).visibility = View.VISIBLE
        findViewById<View>(R.id.bottom_sheet_button_progress).visibility = View.GONE
    }
}
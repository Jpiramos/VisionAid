package com.google.mediapipe.examples.objectdetection


import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.util.Locale
import kotlin.math.roundToInt


private var detectedObjects: List<ObjectDetectorResult> = emptyList()


class ObjectDetectorHelper(
    var threshold: Float = THRESHOLD_DEFAULT,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_EFFICIENTDETV0,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    var objectDetectorListener: DetectorListener? = null
) : TextToSpeech.OnInitListener {

    private var objectDetector: ObjectDetector? = null
    private var imageRotation = 0
    private lateinit var imageProcessingOptions: ImageProcessingOptions
    private lateinit var textToSpeech: TextToSpeech
    private var lastSpokenTime: Long = 0
    private val minIntervalBetweenSpeaks: Long = 3000 // Intervalo mínimo entre falas em milissegundos


    private val objectTranslations = mapOf(
        "person" to "pessoa",
        "bicycle" to "bicicleta",
        "car" to "carro",
        "motorcycle" to "moto",
        "airplane" to "avião",
        "bus" to "ônibus",
        "train" to "trem",
        "truck" to "caminhão",
        "boat" to "barco",
        "traffic light" to "semáforo",
        "fire hydrant" to "hidrante",
        "stop sign" to "placa de pare",
        "parking meter" to "parquímetro",
        "bench" to "banco",
        "bird" to "pássaro",
        "cat" to "gato",
        "dog" to "cachorro",
        "horse" to "cavalo",
        "sheep" to "ovelha",
        "cow" to "vaca",
        "elephant" to "elefante",
        "bear" to "urso",
        "zebra" to "zebra",
        "giraffe" to "girafa",
        "backpack" to "mochila",
        "umbrella" to "guarda-chuva",
        "handbag" to "bolsa",
        "tie" to "gravata",
        "suitcase" to "mala",
        "frisbee" to "disco de frisbee",
        "skis" to "esquis",
        "snowboard" to "prancha de snowboard",
        "sports ball" to "bola de esporte",
        "kite" to "pipa",
        "baseball bat" to "taco de baseball",
        "baseball glove" to "luva de baseball",
        "skateboard" to "skate",
        "surfboard" to "prancha de surfe",
        "tennis racket" to "raquete de tênis",
        "bottle" to "garrafa",
        "wine glass" to "taça de vinho",
        "cup" to "copo",
        "fork" to "garfo",
        "knife" to "faca",
        "spoon" to "colher",
        "bowl" to "tigela",
        "banana" to "banana",
        "apple" to "maçã",
        "sandwich" to "sanduíche",
        "orange" to "laranja",
        "broccoli" to "brócolis",
        "carrot" to "cenoura",
        "hot dog" to "cachorro-quente",
        "pizza" to "pizza",
        "donut" to "rosquinha",
        "cake" to "bolo",
        "chair" to "cadeira",
        "couch" to "sofá",
        "potted plant" to "planta em vaso",
        "bed" to "cama",
        "dining table" to "mesa de jantar",
        "toilet" to "vaso sanitário",
        "tv" to "televisão",
        "laptop" to "notebook",
        "mouse" to "mouse",
        "remote" to "controle remoto",
        "keyboard" to "teclado",
        "cell phone" to "celular",
        "microwave" to "micro-ondas",
        "oven" to "forno",
        "toaster" to "torradeira",
        "sink" to "pia",
        "refrigerator" to "geladeira",
        "book" to "livro",
        "clock" to "relógio",
        "vase" to "vaso",
        "scissors" to "tesoura",
        "teddy bear" to "urso de pelúcia",
        "hair drier" to "secador de cabelo",
        "toothbrush" to "escova de dentes"

    )
    private val displayMetrics = context.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels
    val screenHeight = displayMetrics.heightPixels

    init {
        setupObjectDetector()
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("pt", "BR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Idioma Português não está disponível.")
                // Aqui você pode instruir o usuário a baixar o pacote de voz para Português
            }
        } else {
            Log.e(TAG, "Falha ao inicializar Text-to-Speech")
        }
    }





    fun clearObjectDetector() {
        objectDetector?.close()
        objectDetector = null
    }

    fun setupObjectDetector() {
        // Set general detection options, including number of used threads
        val baseOptionsBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }

            DELEGATE_GPU -> {
                // Is there a check for GPU being supported?
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName = when (currentModel) {
            MODEL_EFFICIENTDETV0 -> "efficientdet-lite0.tflite"
            MODEL_EFFICIENTDETV2 -> "efficientdet-lite2.tflite"
            else -> "efficientdet-lite0.tflite"
        }

        baseOptionsBuilder.setModelAssetPath(modelName)

        // Check if runningMode is consistent with objectDetectorListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (objectDetectorListener == null) {
                    throw IllegalStateException(
                        "objectDetectorListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }

            RunningMode.IMAGE, RunningMode.VIDEO -> {
                // no-op
            }
        }

        try {
            val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setScoreThreshold(threshold).setRunningMode(runningMode)
                .setMaxResults(maxResults)

            imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageRotation).build()

            when (runningMode) {
                RunningMode.IMAGE, RunningMode.VIDEO -> optionsBuilder.setRunningMode(
                    runningMode
                )

                RunningMode.LIVE_STREAM -> optionsBuilder.setRunningMode(
                    runningMode
                ).setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        } catch (e: RuntimeException) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for " + "details",
                GPU_ERROR
            )
            Log.e(
                TAG,
                "Object detector failed to load model with error: " + e.message
            )
        }
    }

    // Return running status of recognizer helper
    fun isClosed(): Boolean {
        return objectDetector == null
    }


    fun detectVideoFile(
        videoUri: Uri, inferenceIntervalMs: Long
    ): ResultBundle? {

        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile" + " while not using RunningMode.VIDEO"
            )
        }

        if (objectDetector == null) return null

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        // Load frames from the video and run the object detection model.
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()


        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        // If the video is invalid, returns a null detection result
        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        // Next, we'll get one frame every frameInterval ms, then run detection on these frames.
        val resultList = mutableListOf<ObjectDetectorResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            val timestampMs = i * inferenceIntervalMs // ms

            retriever.getFrameAtTime(
                timestampMs * 1000, // convert from ms to micro-s
                MediaMetadataRetriever.OPTION_CLOSEST
            )?.let { frame ->
                // Convert the video frame to ARGB_8888 which is required by the MediaPipe
                val argb8888Frame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)

                // Convert the input Bitmap object to an MPImage object to run inference
                val mpImage = BitmapImageBuilder(argb8888Frame).build()

                // Run object detection using MediaPipe Object Detector API
                objectDetector?.detectForVideo(mpImage, timestampMs)
                    ?.let { detectionResult ->
                        resultList.add(detectionResult)
                    } ?: {
                    didErrorOccurred = true
                    objectDetectorListener?.onError(
                        "ResultBundle could not be returned" + " in detectVideoFile"
                    )
                }
            } ?: run {
                didErrorOccurred = true
                objectDetectorListener?.onError(
                    "Frame at specified time could not be" + " retrieved when detecting in video."
                )
            }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
        }
    }

    // Runs object detection on live streaming cameras frame-by-frame and returns the results
    // asynchronously to the caller.
    fun detectLivestreamFrame(imageProxy: ImageProxy) {

        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLivestreamFrame" + " while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        // If the input image rotation is change, stop all detector
        if (imageProxy.imageInfo.rotationDegrees != imageRotation) {
            imageRotation = imageProxy.imageInfo.rotationDegrees
            clearObjectDetector()
            setupObjectDetector()
            return
        }

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(bitmapBuffer).build()

        detectAsync(mpImage, frameTime)
    }

    // Run object detection using MediaPipe Object Detector API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        // As we're using running mode LIVE_STREAM, the detection result will be returned in
        // returnLivestreamResult function
        objectDetector?.detectAsync(mpImage, imageProcessingOptions, frameTime)
    }

    // Return the detection result to this ObjectDetectorHelper's caller

    private fun returnLivestreamResult(
        result: ObjectDetectorResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        // Atualiza a lista de objetos detectados
        detectedObjects = listOf(result)

        // Extrai os nomes dos objetos detectados, suas posições e traduz para o português
        val detectedObjectsList = result.detections().mapNotNull { detection ->
            if (detection.categories().isNotEmpty()) {
                val category = detection.categories()[0]
                val englishName = category.categoryName() ?: "Objeto desconhecido"
                val translatedName = objectTranslations[englishName] ?: englishName

                // Bounding box do objeto
                val boundingBox = detection.boundingBox()

                val positionDescription = calculatePositionDescription(
                    boundingBox,
                    input.width,
                    input.height,
                    isFrontCamera = false // câmera traseira
                )


                // Retorna nome traduzido + posição
                "$translatedName $positionDescription"
            } else {
                null
            }
        }

        // Cria a string para o Text-to-Speech
        val detectedObjectsString = when {
            detectedObjectsList.isEmpty()   -> "Nenhum objeto detectado"
            detectedObjectsList.size == 1   -> "Objeto detectado: ${detectedObjectsList[0]}"
            else                            -> "Objetos detectados: ${detectedObjectsList.joinToString(", ")}"
        }

        // Fala apenas se passou o intervalo mínimo
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastSpokenTime >= minIntervalBetweenSpeaks && !textToSpeech.isSpeaking) {
            textToSpeech.speak(detectedObjectsString, TextToSpeech.QUEUE_FLUSH, null, "tts1")
            lastSpokenTime = currentTime
        }

        // Envia resultados ao listener
        objectDetectorListener?.onResults(
            ResultBundle(
                detectedObjects,
                inferenceTime,
                input.height,
                input.width,
                imageRotation
            )
        )
    }


    private fun calculatePositionDescription(
        boundingBox: RectF,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean = false // default: câmera traseira
    ): String {
        // 1) Calcula o centro X normalizado [0, 1]
        val centerYImage = (boundingBox.top + boundingBox.bottom) / 2f
        val centerYNorm = centerYImage / imageHeight

        // 2) Ajusta só se for câmera frontal
        val adjustedCenterX = if (isFrontCamera) 1f - centerYNorm else centerYNorm
        val roundedCenterX = (adjustedCenterX * 100).roundToInt() / 100f


        // 3) Decide a posição com base em limites claros
        val horizontal = when {
            roundedCenterX >= 0.60f -> "esquerda"
            roundedCenterX >= 0.40f -> "centro"
            roundedCenterX >= 0.30f -> "direita"
            else -> " "
        }


        // 4) Distância só por altura relativa
        val boxHeightNorm = (boundingBox.height()) / imageHeight.toFloat()
        val distance = if (boxHeightNorm > 0.1f) "perto" else "longe"

        // DEBUG final
        Log.d(
            "PosDebug",
            "adjustedCenterX=$adjustedCenterX, roundedCenterX=$roundedCenterX, horizontal=$horizontal, boxHeightNorm=$boxHeightNorm, distance=$distance"
        )

        return "$horizontal, $distance"
    }














    // Return errors thrown during detection to this ObjectDetectorHelper's caller
    private fun returnLivestreamError(error: RuntimeException) {
        objectDetectorListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    // Accepted a Bitmap and runs object detection inference on it to return results back
    // to the caller
    fun detectImage(image: Bitmap): ResultBundle? {

        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" + " while not using RunningMode.IMAGE"
            )
        }

        if (objectDetector == null) return null

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run object detection using MediaPipe Object Detector API
        objectDetector?.detect(mpImage)?.also { detectionResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                listOf(detectionResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        // If objectDetector?.detect() returns null, this is likely an error. Returning null
        // to indicate this.
        return null
    }

    // Wraps results from inference, the time it takes for inference to be performed, and
    // the input image and height for properly scaling UI to return back to callers
    data class ResultBundle(
        val results: List<ObjectDetectorResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inputImageRotation: Int = 0
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTDETV0 = 0
        const val MODEL_EFFICIENTDETV2 = 1
        const val MAX_RESULTS_DEFAULT = 3
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        const val TAG = "ObjectDetectorHelper"
    }

    // Used to pass results or errors back to the calling class
    interface DetectorListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
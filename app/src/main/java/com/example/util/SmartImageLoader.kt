package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Двухуровневый загрузчик и кэшер обложек треков (RAM L1 + Дисковый L2 кэш).
 * Обеспечивает мгновенный доступ к обложкам в ОЗУ сразу при запуске приложения
 * и мгновенную отрисовку без задержек в Compose.
 */
class SmartImageLoader private constructor(private val context: Context) {

    companion object {
        const val THUMBNAIL_SIZE = 200

        @Volatile
        private var instance: SmartImageLoader? = null

        fun getInstance(context: Context): SmartImageLoader {
            return instance ?: synchronized(this) {
                instance ?: SmartImageLoader(context.applicationContext).also { instance = it }
            }
        }
    }

    // Кэш первого уровня (RAM) с ограничением по объему памяти
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 6 // До 1/6 оперативной памяти для быстрого кэша

    private val ramCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // Кэш скомпилированных ImageBitmap для мгновенной отрисовки в Jetpack Compose без повторной конверсии
    private val composeImageCache = ConcurrentHashMap<String, ImageBitmap>()

    // Пул потоков для фоновой загрузки, декодирования и дискового кэширования
    private val executorService: ExecutorService = Executors.newFixedThreadPool(3)

    // Директория для дискового кэша (L2)
    private val cacheDir: File = File(context.cacheDir, "artwork_cache").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    /**
     * Получение безопасного имени файла для дискового кэша
     */
    private fun getCacheKey(idOrUri: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(idOrUri.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            idOrUri.replace("[^a-zA-Z0-9]".toRegex(), "_")
        }
    }

    /**
     * Получить изображение из RAM-кэша синхронно (если есть)
     */
    fun getFromRam(key: String): Bitmap? {
        return ramCache.get(key)
    }

    /**
     * Получить готовый ImageBitmap для Jetpack Compose синхронно
     */
    fun getComposeImageFromRam(key: String): ImageBitmap? {
        val cached = composeImageCache[key]
        if (cached != null) return cached

        val bitmap = ramCache.get(key) ?: return null
        val imageBitmap = bitmap.asImageBitmap()
        composeImageCache[key] = imageBitmap
        return imageBitmap
    }

    /**
     * Асинхронная загрузка обложки трека с двухуровневым кэшированием (RAM -> Disk -> Extraction)
     */
    suspend fun loadCover(trackUriString: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (trackUriString.isNullOrBlank()) return@withContext null

        // 1. Проверка оперативного кэша (RAM)
        val cachedRam = ramCache.get(trackUriString)
        if (cachedRam != null) {
            if (!composeImageCache.containsKey(trackUriString)) {
                composeImageCache[trackUriString] = cachedRam.asImageBitmap()
            }
            return@withContext cachedRam
        }

        val cacheKey = getCacheKey(trackUriString)
        val diskFile = File(cacheDir, "$cacheKey.png")

        // 2. Проверка дискового кэша
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (diskBitmap != null) {
                    ramCache.put(trackUriString, diskBitmap)
                    composeImageCache[trackUriString] = diskBitmap.asImageBitmap()
                    return@withContext diskBitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Извлечение оригинальной обложки из аудиофайла
        val rawBitmap = extractEmbeddedPicture(trackUriString) ?: return@withContext null

        // 4. Масштабирование до 200x200
        val scaledBitmap = try {
            if (rawBitmap.width != THUMBNAIL_SIZE || rawBitmap.height != THUMBNAIL_SIZE) {
                Bitmap.createScaledBitmap(rawBitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            rawBitmap
        }

        // 5. Сохранение на диск в формате PNG
        try {
            FileOutputStream(diskFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 6. Помещение в оперативный кэш
        ramCache.put(trackUriString, scaledBitmap)
        composeImageCache[trackUriString] = scaledBitmap.asImageBitmap()

        return@withContext scaledBitmap
    }

    /**
     * Фоновая предварительная загрузка обложек в RAM-кэш (L1) при запуске приложения.
     * Быстро подгружает готовые миниатюры из дискового кэша (L2) или извлекает их,
     * чтобы скролл и интерфейс были абсолютно плавными без задержек.
     */
    suspend fun preloadCovers(tracks: List<Track>) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        for (track in tracks) {
            val uriString = track.uriString
            if (ramCache.get(uriString) == null) {
                try {
                    loadCover(uriString)
                } catch (e: Exception) {
                    // Игнорируем единичные ошибки декодирования
                }
            }
        }
    }

    /**
     * Асинхронная предварительная загрузка через пул потоков
     */
    fun preloadCoversAsync(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        executorService.submit {
            for (track in tracks) {
                val uriString = track.uriString
                if (ramCache.get(uriString) == null) {
                    try {
                        val cacheKey = getCacheKey(uriString)
                        val diskFile = File(cacheDir, "$cacheKey.png")

                        if (diskFile.exists() && diskFile.length() > 0) {
                            val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                            if (diskBitmap != null) {
                                ramCache.put(uriString, diskBitmap)
                                continue
                            }
                        }

                        val raw = extractEmbeddedPicture(uriString)
                        if (raw != null) {
                            val scaled = try {
                                if (raw.width != THUMBNAIL_SIZE || raw.height != THUMBNAIL_SIZE) {
                                    Bitmap.createScaledBitmap(raw, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
                                } else {
                                    raw
                                }
                            } catch (e: Exception) {
                                raw
                            }
                            try {
                                FileOutputStream(diskFile).use { out ->
                                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    out.flush()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            ramCache.put(uriString, scaled)
                        }
                    } catch (e: Exception) {
                        // ignore and continue
                    }
                }
            }
        }
    }

    /**
     * Извлечение метаданных обложки через MediaMetadataRetriever с эффективным downsampling (inSampleSize)
     */
    private fun extractEmbeddedPicture(uriString: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            val artBytes = if (uri.scheme == "content" || uri.scheme == "file") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    retriever.embeddedPicture
                }
            } else {
                retriever.setDataSource(uriString)
                retriever.embeddedPicture
            }

            if (artBytes != null) {
                decodeSampledBitmapFromByteArray(artBytes, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            } else {
                // Пробуем декодировать как обычный файл изображения с downsampling
                val file = File(uriString)
                if (file.exists()) {
                    decodeSampledBitmapFromFile(file.absolutePath, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                } else null
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Эффективное декодирование массива байтов с расчетом inSampleSize для минимизации нагрузки на RAM и GC
     */
    private fun decodeSampledBitmapFromByteArray(data: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565 // 50% экономия RAM без потери видимого качества

        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } catch (e: OutOfMemoryError) {
            System.gc()
            null
        }
    }

    /**
     * Эффективное декодирование файла с расчетом inSampleSize
     */
    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        return try {
            BitmapFactory.decodeFile(path, options)
        } catch (e: OutOfMemoryError) {
            System.gc()
            null
        }
    }

    /**
     * Расчет оптимального коэффициента масштабирования (степени двойки) для декодера
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Загрузка обложки через обратный вызов (callback) для фонового пула
     */
    fun loadCoverAsync(trackUriString: String?, onLoaded: (Bitmap?) -> Unit) {
        if (trackUriString.isNullOrBlank()) {
            onLoaded(null)
            return
        }

        val cachedRam = ramCache.get(trackUriString)
        if (cachedRam != null) {
            onLoaded(cachedRam)
            return
        }

        executorService.submit {
            val cacheKey = getCacheKey(trackUriString)
            val diskFile = File(cacheDir, "$cacheKey.png")

            if (diskFile.exists() && diskFile.length() > 0) {
                try {
                    val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                    if (diskBitmap != null) {
                        ramCache.put(trackUriString, diskBitmap)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onLoaded(diskBitmap)
                        }
                        return@submit
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val raw = extractEmbeddedPicture(trackUriString)
            if (raw != null) {
                val scaled = try {
                    Bitmap.createScaledBitmap(raw, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
                } catch (e: Exception) {
                    raw
                }
                try {
                    FileOutputStream(diskFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.flush()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                ramCache.put(trackUriString, scaled)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onLoaded(scaled)
                }
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onLoaded(null)
                }
            }
        }
    }

    /**
     * Очистка кэша
     */
    fun clearCache() {
        ramCache.evictAll()
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Jetpack Compose хук для автоматической реактивной подгрузки обложки через SmartImageLoader (Bitmap).
 */
@Composable
fun rememberSmartCover(uriString: String?): Bitmap? {
    if (uriString == null) return null
    val context = LocalContext.current
    val loader = remember { SmartImageLoader.getInstance(context) }
    val cached = loader.getFromRam(uriString)
    var bitmap by remember(uriString) { mutableStateOf(cached) }

    if (bitmap == null) {
        LaunchedEffect(uriString) {
            val loaded = loader.loadCover(uriString)
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    return bitmap ?: cached
}

/**
 * Jetpack Compose хук для прямого получения ImageBitmap без пересчета на каждый кадр.
 */
@Composable
fun rememberSmartImageBitmap(uriString: String?): ImageBitmap? {
    if (uriString == null) return null
    val context = LocalContext.current
    val loader = remember { SmartImageLoader.getInstance(context) }
    val cached = loader.getComposeImageFromRam(uriString)
    var imageBitmap by remember(uriString) { mutableStateOf(cached) }

    if (imageBitmap == null) {
        LaunchedEffect(uriString) {
            val loaded = loader.loadCover(uriString)
            if (loaded != null) {
                imageBitmap = loader.getComposeImageFromRam(uriString)
            }
        }
    }

    return imageBitmap ?: cached
}

/**
 * Компонент обложки с использованием SmartImageLoader и плейсхолдером.
 */
@Composable
fun SmartArtworkImage(
    uriString: String?,
    modifier: Modifier = Modifier,
    contentDescription: String = "Cover Art"
) {
    val imageBitmap = rememberSmartImageBitmap(uriString)

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

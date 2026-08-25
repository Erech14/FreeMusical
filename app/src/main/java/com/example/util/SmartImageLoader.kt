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
 * Обеспечивает мгновенный доступ к обложкам после первого запуска и сохранение миниатюр (200x200) в формате PNG.
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
    private val cacheSize = maxMemory / 8 // 1/8 от всей доступной оперативной памяти
    private val ramCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // Пул потоков для фоновой загрузки, декодирования и дискового кэширования
    private val executorService: ExecutorService = Executors.newFixedThreadPool(4)

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
     * Асинхронная загрузка обложки трека с двухуровневым кэшированием (RAM -> Disk -> Extraction)
     */
    suspend fun loadCover(trackUriString: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (trackUriString.isNullOrBlank()) return@withContext null

        // 1. Проверка оперативного кэша (RAM)
        val cachedRam = ramCache.get(trackUriString)
        if (cachedRam != null) {
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

        return@withContext scaledBitmap
    }

    /**
     * Извлечение метаданных обложки через MediaMetadataRetriever
     */
    private fun extractEmbeddedPicture(uriString: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content" || uri.scheme == "file") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) {
                        BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    } else null
                }
            } else {
                retriever.setDataSource(uriString)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                } else {
                    // Пробуем декодировать как обычный файл изображения
                    val file = File(uriString)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else null
                }
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
 * Jetpack Compose хук для автоматической реактивной подгрузки обложки через SmartImageLoader.
 */
@Composable
fun rememberSmartCover(uriString: String?): Bitmap? {
    val context = LocalContext.current
    val loader = remember { SmartImageLoader.getInstance(context) }
    var bitmap by remember(uriString) {
        mutableStateOf(if (uriString != null) loader.getFromRam(uriString) else null)
    }

    LaunchedEffect(uriString) {
        if (uriString != null) {
            val loaded = loader.loadCover(uriString)
            bitmap = loaded
        } else {
            bitmap = null
        }
    }

    return bitmap
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
    val bitmap = rememberSmartCover(uriString)

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
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

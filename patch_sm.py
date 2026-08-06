import sys

with open('app/src/main/java/com/example/ui/MainScreen.kt', 'r') as f:
    content = f.read()

target = """                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            val sharedMemory = SharedMemory.create("artwork", artBytes.size)
                            val buffer = sharedMemory.mapReadWrite()
                            buffer.put(artBytes)
                            SharedMemory.unmap(buffer)
                            
                            var decoded: Bitmap? = null
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val readBuffer = sharedMemory.mapReadOnly()
                                try {
                                    val source = android.graphics.ImageDecoder.createSource(readBuffer)
                                    decoded = android.graphics.ImageDecoder.decodeBitmap(source)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    SharedMemory.unmap(readBuffer)
                                }
                            } else {
                                try {
                                    val fdMethod = sharedMemory.javaClass.getMethod("getFileDescriptor")
                                    val fd = fdMethod.invoke(sharedMemory) as java.io.FileDescriptor
                                    BitmapFactory.decodeFileDescriptor(fd, null, options)
                                    
                                    var inSampleSize = 1
                                    val reqWidth = 150
                                    val reqHeight = 150
                                    if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                                        val halfHeight = options.outHeight / 2
                                        val halfWidth = options.outWidth / 2
                                        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                                            inSampleSize *= 2
                                        }
                                    }
                                    
                                    options.inJustDecodeBounds = false
                                    options.inSampleSize = inSampleSize
                                    
                                    decoded = BitmapFactory.decodeFileDescriptor(fd, null, options)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            sharedMemory.close()
                            
                            if (decoded != null) {
                                ArtworkCache.cache.put(uriString, decoded)
                                bitmap = decoded
                            }
                        } else {
                            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                            
                            var inSampleSize = 1
                            val reqWidth = 150
                            val reqHeight = 150
                            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                                val halfHeight = options.outHeight / 2
                                val halfWidth = options.outWidth / 2
                                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                                    inSampleSize *= 2
                                }
                            }
                            
                            options.inJustDecodeBounds = false
                            options.inSampleSize = inSampleSize
                            
                            val decoded = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                            if (decoded != null) {
                                ArtworkCache.cache.put(uriString, decoded)
                                bitmap = decoded
                            }
                        }"""

replacement = """                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                        
                        var inSampleSize = 1
                        val reqWidth = 150
                        val reqHeight = 150
                        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                            val halfHeight = options.outHeight / 2
                            val halfWidth = options.outWidth / 2
                            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                                inSampleSize *= 2
                            }
                        }
                        
                        options.inJustDecodeBounds = false
                        options.inSampleSize = inSampleSize
                        
                        val decoded = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                        if (decoded != null) {
                            ArtworkCache.cache.put(uriString, decoded)
                            bitmap = decoded
                        }"""

if target in content:
    content = content.replace(target, replacement)
    with open('app/src/main/java/com/example/ui/MainScreen.kt', 'w') as f:
        f.write(content)
    print("Success")
else:
    print("Target not found")

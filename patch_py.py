import re

with open('app/src/main/java/com/example/player/MusicPlayerEngine.kt', 'r') as f:
    content = f.read()

content = content.replace('if (focusGranted) {\n                    start()\n                }', 'if (focusGranted) {\n                    start()\n                    startPlaybackService(context)\n                }')

content = content.replace('player.start()\n                        _isPlaying.value = true\n                        startProgressTracker()', 'player.start()\n                        _isPlaying.value = true\n                        startPlaybackService(context)\n                        startProgressTracker()')

# Add the method
method = """
    private fun startPlaybackService(context: Context) {
        try {
            val serviceIntent = android.content.Intent(context, PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}"""

content = re.sub(r'\}\s*$', method, content)

with open('app/src/main/java/com/example/player/MusicPlayerEngine.kt', 'w') as f:
    f.write(content)

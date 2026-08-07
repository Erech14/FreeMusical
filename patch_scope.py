import re

with open('app/src/main/java/com/example/player/PlaybackService.kt', 'r') as f:
    content = f.read()

content = content.replace('class PlaybackService : Service() {', 'class PlaybackService : Service() {\n    private var job: kotlinx.coroutines.Job? = null')

content = content.replace('GlobalScope.launch(Dispatchers.Main) {', 'kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {\n            job = coroutineContext[kotlinx.coroutines.Job]')

content = content.replace('return START_STICKY\n    }', 'return START_STICKY\n    }\n\n    override fun onDestroy() {\n        super.onDestroy()\n        job?.cancel()\n    }')

with open('app/src/main/java/com/example/player/PlaybackService.kt', 'w') as f:
    f.write(content)

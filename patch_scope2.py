import re

with open('app/src/main/java/com/example/player/PlaybackService.kt', 'r') as f:
    content = f.read()

content = content.replace('kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {', 'job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {')

with open('app/src/main/java/com/example/player/PlaybackService.kt', 'w') as f:
    f.write(content)

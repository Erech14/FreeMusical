with open('app/src/main/java/com/example/player/PlaybackService.kt', 'r') as f:
    content = f.read()

imports = """
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
"""

content = content.replace('import com.example.MainActivity\n', 'import com.example.MainActivity\n' + imports)

with open('app/src/main/java/com/example/player/PlaybackService.kt', 'w') as f:
    f.write(content)

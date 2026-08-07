import re

with open('app/src/main/java/com/example/player/PlaybackService.kt', 'r') as f:
    content = f.read()

replacement = """
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle(track.title)
            .setContentText(track.artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
"""

content = re.sub(r'val builder = Notification.Builder\(this, CHANNEL_ID\)\s*\.setContentTitle.*?\.setVisibility\(Notification\.VISIBILITY_PUBLIC\)', replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/player/PlaybackService.kt', 'w') as f:
    f.write(content)

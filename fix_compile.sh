sed -i 's/fun ArtworkImage(/@Composable\nfun ArtworkImage(/g' app/src/main/java/com/example/ui/MainScreen.kt
sed -i 's/Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)/android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)/g' app/src/main/java/com/example/ui/MainScreen.kt

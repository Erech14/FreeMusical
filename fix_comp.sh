sed -i '2005,2006d' app/src/main/java/com/example/ui/MainScreen.kt
sed -i '/fun ArtworkImage/i \
@Composable' app/src/main/java/com/example/ui/MainScreen.kt

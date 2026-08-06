sed -i '/fun rememberTrackArtwork/i \
@Composable\
fun ArtworkImage(\
    uriString: String?,\
    modifier: Modifier = Modifier\
) {\
    val context = LocalContext.current\
    val artworkBitmap = rememberTrackArtwork(context, uriString)\
\
    if (artworkBitmap != null) {\
        Image(\
            bitmap = artworkBitmap.asImageBitmap(),\
            contentDescription = "Cover Art",\
            modifier = modifier,\
            contentScale = ContentScale.Crop\
        )\
    } else {\
        Image(\
            painter = painterResource(id = R.drawable.ic_app_logo),\
            contentDescription = "Cover Art",\
            modifier = modifier,\
            contentScale = ContentScale.Crop\
        )\
    }\
}\
' app/src/main/java/com/example/ui/MainScreen.kt

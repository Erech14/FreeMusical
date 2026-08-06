import sys
import re

with open('app/src/main/java/com/example/ui/MainScreen.kt', 'r') as f:
    content = f.read()

pattern = re.compile(r'    // Storage permission launcher depending on SDK.*?    // Identify if current folder is the "Главный"', re.DOTALL)

replacement = """    // Storage permission launcher depending on SDK
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.entries.all { it.value }
        viewModel.setPermissionGranted(isGranted)
        if (isGranted && selectedFolderUri == null) {
            try {
                folderPickerLauncher.launch(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Check permission on startup
    LaunchedEffect(Unit) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissionsToRequest.all { 
                context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
            }
        } else {
            true
        }
        viewModel.setPermissionGranted(hasPermission)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Identify if current folder is the "Главный\""""

content = pattern.sub(replacement, content)

with open('app/src/main/java/com/example/ui/MainScreen.kt', 'w') as f:
    f.write(content)

package com.example

import android.app.Application
import com.example.ui.Logger

/**
 * Точка входа приложения: как можно раньше поднимает логгер, чтобы фиксировать
 * сервисную информацию, файловые логи и необработанные исключения начиная
 * с самого старта процесса.
 */
class FreeMusicalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        Logger.info("Application.onCreate (pid=${android.os.Process.myPid()})", tag = "App")
    }
}

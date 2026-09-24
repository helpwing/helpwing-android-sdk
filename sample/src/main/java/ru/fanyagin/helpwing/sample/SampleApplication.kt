package ru.fanyagin.helpwing.sample

import android.app.Application
import ru.fanyagin.helpwing.Helpwing
import ru.fanyagin.helpwing.HelpwingSettings

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 10.0.2.2 is the host machine from the emulator. Replace the key with your project's.
        Helpwing.initialize(this, HelpwingSettings(apiUrl = "http://10.0.2.2:8000", projectKey = "pk_test_replace_me"))
    }
}

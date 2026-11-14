package org.klaipher.nanogram.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

// Runs against an emulator that is not logged in, so it covers the logged-out start flow only; post-login paths are
// hand-written in TMessagesProj_App/src/main/baseline-prof.txt.
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    // Cold start to the first screen; also feeds the R8 startup profile (dex layout).
    @Test
    fun startup() = rule.collect(PACKAGE, includeInStartupProfile = true) {
        coldStart()
        waitForText(START_MESSAGING)
    }

    @Test
    fun loggedOutFlow() = rule.collect(PACKAGE, maxIterations = 6, stableIterations = 2) {
        coldStart()
        swipeIntro()
        waitForText(START_MESSAGING)?.click()
        dismissPermissionDialogs()
        waitForDesc(DIAGNOSTICS)
        openCountryPicker()
        openDiagnostics()
    }

    private fun MacrobenchmarkScope.coldStart() {
        pressHome()
        startActivityAndWait()
        dismissPermissionDialogs()
    }

    private fun MacrobenchmarkScope.swipeIntro() {
        waitForText(START_MESSAGING)
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(5) {
            device.swipe((w * 0.85).toInt(), (h * 0.4).toInt(), (w * 0.15).toInt(), (h * 0.4).toInt(), 12)
            device.waitForIdle(500)
            Thread.sleep(400)
        }
    }

    private fun MacrobenchmarkScope.openCountryPicker() {
        waitForDesc(COUNTRY)?.click() ?: return
        device.waitForIdle(1500)
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(3) {
            device.swipe(w / 2, (h * 0.8).toInt(), w / 2, (h * 0.3).toInt(), 15)
            device.waitForIdle(500)
        }
        backTo(DIAGNOSTICS)
    }

    private fun MacrobenchmarkScope.openDiagnostics() {
        waitForDesc(DIAGNOSTICS)?.click() ?: return
        device.waitForIdle(1500)
        Thread.sleep(1000)
        backTo(DIAGNOSTICS)
    }

    // Back until the login screen shows again; at most twice so the app is never left.
    private fun MacrobenchmarkScope.backTo(desc: String) {
        repeat(2) {
            if (device.hasObject(By.desc(desc))) return
            device.pressBack()
            device.waitForIdle(500)
        }
    }

    // System permission dialogs: always "Don't allow", so the profile never depends on a granted permission.
    private fun MacrobenchmarkScope.dismissPermissionDialogs() {
        val deny = By.text(Pattern.compile("(?i)don.?t allow|deny"))
        repeat(4) {
            val button = device.wait(Until.findObject(deny), 1500) ?: return
            button.click()
            device.waitForIdle(500)
        }
    }

    private fun MacrobenchmarkScope.waitForText(text: String) =
        device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS)

    private fun MacrobenchmarkScope.waitForDesc(desc: String) =
        device.wait(Until.findObject(By.desc(desc)), UI_TIMEOUT_MS)

    private companion object {
        const val PACKAGE = "org.klaipher.nanogram"
        const val START_MESSAGING = "Start Messaging"
        const val COUNTRY = "Country"
        const val DIAGNOSTICS = "Send diagnostics"
        const val UI_TIMEOUT_MS = 15_000L
    }
}

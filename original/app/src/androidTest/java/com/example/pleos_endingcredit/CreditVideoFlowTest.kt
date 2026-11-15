package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.File
import java.net.URL
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditVideoFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = TripStore(context)
    private val demo = context.packageName.endsWith(".demo")

    @Test fun waitingAndSkipShowTheExpectedFilm() = withNewCredit(crewName = "가족 크루") { scenario ->
        assertEquals("아직 받은 영상이 없어요", text(scenario, "b1.status"))
        scenario.onActivity { activity ->
            assertFalse(activity.window.decorView.findViewWithTag<View>("b1.make").isEnabled)
            val prompt = activity.window.decorView.findViewWithTag<View>("b1.credit.video")
            assertEquals("B1", (prompt.parent as View).getTag(R.id.ending_credit_screen_id))
        }
        screenshot("waiting")
        click(scenario, "b1.skip")
        checkFilm(scenario, uploaded = false, companions = "가족 크루")
        assertAnimatedTimeline(scenario, "without-video")
        click(scenario, "b3.next")
        await(scenario) { it.findViewWithTag<View>("c1.result")?.isShown == true }
    }

    @Test fun receivedVideoUsesTheExpectedFilm() = withNewCredit { scenario ->
        if (demo) {
            click(scenario, "demo.toggle")
            click(scenario, "demo.video.insert")
        } else {
            val url = readUploadQr(scenario)
            assertEquals(404, request(url + "-invalid", null))
            assertEquals(200, request(url, null))
            assertEquals(422, request(url, "invalid".toByteArray()))
            assertEquals("아직 받은 영상이 없어요", text(scenario, "b1.status"))
            val sample = context.assets.open("b3/v2/cat.mp4").use { it.readBytes() }
            assertEquals(200, request(url, sample))
        }
        await(scenario) { it.findViewWithTag<View>("b1.make")?.isEnabled == true }
        assertEquals("영상을 받았어요", text(scenario, "b1.status"))
        assertEquals("0:12 · 앞 12초가 들어가요", text(scenario, "b1.received.duration"))
        screenshot("received")
        scenario.recreate()
        await(scenario) { it.findViewWithTag<View>("b1.make")?.isEnabled == true }
        assertEquals("영상을 받았어요", text(scenario, "b1.status"))
        scenario.onActivity { activity ->
            val prompt = activity.window.decorView.findViewWithTag<View>("b1.credit.video")
            assertEquals("B1-1", (prompt.parent as View).getTag(R.id.ending_credit_screen_id))
        }
        click(scenario, "b1.make")
        checkFilm(scenario, uploaded = true, companions = "4명")
        assertAnimatedTimeline(scenario, "with-video")
        click(scenario, "b3.video.play")
        var pausedAt = 0L
        scenario.onActivity {
            pausedAt = it.window.decorView.findViewWithTag<B3CreditView>("b3.credit").elapsedMillis
        }
        SystemClock.sleep(350)
        scenario.onActivity {
            assertEquals(pausedAt, it.window.decorView.findViewWithTag<B3CreditView>("b3.credit").elapsedMillis)
        }
        click(scenario, "b3.video.play")
        await(scenario) { it.findViewWithTag<B3CreditView>("b3.credit")?.elapsedMillis?.let { time -> time > pausedAt } == true }
        click(scenario, "b3.next")
        await(scenario) { it.findViewWithTag<View>("c1.result")?.isShown == true }
    }

    private fun withNewCredit(
        crewName: String? = null,
        check: (ActivityScenario<MainActivity>) -> Unit,
    ) {
        val previous = store.resume
        val id = (if (demo) "showcase-spread-" else "") + java.util.UUID.randomUUID()
        val trip = Trip(id = id, name = "우리의 강릉 여행", people = 4,
            creditVideo = CreditVideoChoice.PENDING, crewName = crewName)
        val prefs = context.getSharedPreferences("ending_credit_mock", 0)
        val consent = prefs.getBoolean("consent", false)
        val active = prefs.getString("active_trip", null)
        val repository = TripRecordingRepository.get(context)
        try {
            store.complete(trip)
            val startAt = 1_794_660_000_000L
            repository.sensor(TripRecording(id, startAt, endedAt = startAt + 3_600_000,
                stops = listOf(RecordedStop("qa-stop", startAt + 1_800_000, null, userLabel = "테스트 방문지")),
                departed = true, distanceMeters = 34_800.0, movingMillis = 2_400_000), force = true)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val type = MainActivity::class.java
                    type.getDeclaredField("tripId").apply { isAccessible = true }.set(activity, id)
                    type.getDeclaredMethod("show", Page::class.java).apply { isAccessible = true }
                        .invoke(activity, Page.B1_CREDIT_VIDEO)
                }
                await(scenario) { it.findViewWithTag<View>("b1.skip")?.isShown == true }
                check(scenario)
            }
        } finally {
            repository.delete(id)
            store.deleteCompleted(id)
            store.resume = previous
            prefs.edit().putBoolean("consent", consent).putString("active_trip", active).commit()
            CreditVideoFiles(context).committed(id).delete()
        }
    }

    private fun checkFilm(scenario: ActivityScenario<MainActivity>, uploaded: Boolean, companions: String) {
        await(scenario) { it.findViewWithTag<View>("b2.progress")?.isShown == true }
        screenshot("preparing")
        await(scenario) { it.findViewWithTag<View>("b3.roll")?.isShown == true }
        scenario.onActivity { activity ->
            val view = activity.window.decorView.findViewWithTag<B3CreditView>("b3.credit")
            assertEquals(5, view.frame)
            assertEquals("B3-3", (view.parent as View).getTag(R.id.ending_credit_screen_id))
            val timeline = view.findViewWithTag<View>("b3.roll").contentDescription.toString()
            assertTrue("Closing credit should show $companions", timeline.endsWith("$companions, THE END"))
            assertTrue(timeline.contains("테스트 방문지"))
            assertNull(view.findViewWithTag<View>("b3.chapter.5"))
            assertNull(view.findViewWithTag<View>("b3.video.bridge"))
            assertEquals(uploaded && demo, view.findViewWithTag<View>("b3.video.cat") != null)
            assertEquals(uploaded && !demo, view.findViewWithTag<View>("b3.video.uploaded") != null)
        }
        if (uploaded) {
            await(scenario) { root ->
                val tag = if (demo) "b3.video.cat" else "b3.video.uploaded"
                val video = root.findViewWithTag<TextureView>(tag) ?: return@await false
                val image = video.getBitmap(40, 40) ?: return@await false
                try {
                    (8..30 step 4).any { x -> (8..30 step 4).any { y -> Color.alpha(image.getPixel(x, y)) > 0 } }
                } finally {
                    image.recycle()
                }
            }
        }
    }

    private fun assertAnimatedTimeline(scenario: ActivityScenario<MainActivity>, name: String) {
        scenario.onActivity { activity ->
            val view = activity.window.decorView.findViewWithTag<B3CreditView>("b3.credit")
            val roll = view.findViewWithTag<View>("b3.roll")
            val capture = Bitmap.createBitmap(roll.width, roll.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(capture)
            fun railBottom(): Int {
                capture.eraseColor(Color.TRANSPARENT)
                roll.draw(canvas)
                val x = (roll.width * 279.8f / 680f).toInt()
                return (roll.height - 1 downTo 0).firstOrNull { y ->
                    val color = capture.getPixel(x, y)
                    Color.alpha(color) > 155 && Color.blue(color) > 155 && Color.red(color) < 80
                } ?: -1
            }
            try {
                view.setPlaybackAllowed(false)
                view.seekTo(0)
                assertEquals("The blue marker starts above the visible rail", -1, railBottom())
                view.seekTo(1_450)
                val entrance = railBottom()
                assertTrue("The blue marker enters through the upper empty space", entrance in 1 until roll.height / 4)
                view.seekTo(3_150)
                val descending = railBottom()
                assertTrue("The marker descends before it joins the stops", descending > entrance)
                view.seekTo(4_650)
                val approaching = railBottom()
                view.seekTo(4_900)
                val joined = railBottom()
                view.seekTo(5_150)
                val following = railBottom()
                val entryMovement = joined - approaching
                val followMovement = following - joined
                assertTrue("The marker keeps moving after it joins the first stop", following > joined && joined > descending)
                assertTrue("The marker carries its speed across the first stop",
                    kotlin.math.abs(entryMovement - followMovement) <= roll.height / 150)
                view.seekTo(9_250)
                val middle = railBottom()
                assertTrue("The blue line should reach the middle of the scrolling credits", middle > roll.height / 3)
                view.seekTo(20_000)
                val ending = railBottom()
                assertTrue("The blue line should move up with the last stop", ending in 1 until middle)
                view.seekTo(9_250)
                view.setPlaybackAllowed(true)
            } finally {
                capture.recycle()
            }
        }
        screenshot(name)
    }

    private fun readUploadQr(scenario: ActivityScenario<MainActivity>): String {
        var result: String? = null
        await(scenario) { it.findViewWithTag<ImageView>("b1.qr")?.drawable != null }
        scenario.onActivity { activity ->
            val image = activity.window.decorView.findViewWithTag<ImageView>("b1.qr")
            val bitmap = (image.drawable as android.graphics.drawable.BitmapDrawable).bitmap
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        }
        return requireNotNull(result)
    }

    private fun request(url: String, bytes: ByteArray?): Int {
        // The browser uses HTTP within the local network, so open a raw HTTP connection in the test.
        val target = URL(url)
        java.net.Socket(target.host, target.port).use { socket ->
            socket.soTimeout = 20_000
            val verb = if (bytes == null) "GET" else "POST"
            val output = socket.getOutputStream()
            val length = if (bytes == null) "" else
                "Content-Type: application/octet-stream\r\nContent-Length: ${bytes.size}\r\n"
            output.write("$verb ${target.path} HTTP/1.1\r\nHost: ${target.host}\r\n${length}Connection: close\r\n\r\n".toByteArray())
            if (bytes != null) output.write(bytes)
            output.flush()
            val status = socket.getInputStream().bufferedReader().readLine().split(' ')[1].toInt()
            return status
        }
    }

    private fun click(scenario: ActivityScenario<MainActivity>, tag: String) {
        SystemClock.sleep(330)
        await(scenario) { it.findViewWithTag<View>(tag)?.let { view -> view.isShown && view.isEnabled } == true }
        scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<View>(tag).performClick()) }
    }

    private fun text(scenario: ActivityScenario<MainActivity>, tag: String): String? {
        var value: String? = null
        scenario.onActivity { value = it.window.decorView.findViewWithTag<TextView>(tag)?.text?.toString() }
        return value
    }

    private fun await(scenario: ActivityScenario<MainActivity>, ready: (View) -> Boolean) {
        val timeout = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < timeout) {
            var result = false
            scenario.onActivity { result = ready(it.window.decorView) }
            if (result) return
            SystemClock.sleep(80)
        }
        throw AssertionError("Timed out waiting for the credit video flow")
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(450)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(context.cacheDir, "credit-video-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

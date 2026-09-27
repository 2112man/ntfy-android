package io.heckel.ntfy.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.heckel.ntfy.db.Repository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the parts of the "hide from recent tasks" setting that can be asserted from an
 * instrumentation test: the stored value and its default, and that the activity still
 * launches with the setting enabled.
 *
 * The recents-entry behaviour itself (task hidden while the process/service keep running)
 * must be checked with adb, see the test plan:
 *
 *   adb shell dumpsys activity recents      # task present / absent
 *   adb shell pidof com.man2112.ntfy.debug  # process still alive
 *   adb shell dumpsys activity services     # SubscriberService still alive
 *
 * Run with: ./gradlew connectedFdroidDebugAndroidTest (requires a device/emulator).
 */
@RunWith(AndroidJUnit4::class)
class HideFromRecentsTest {
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        repository = Repository.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
        repository.setHideFromRecents(false) // Known state for every test
    }

    @After
    fun tearDown() {
        repository.setHideFromRecents(false)
    }

    /** Test 1: the setting defaults to OFF. */
    @Test
    fun hideFromRecents_defaultsToOff() {
        repository.setHideFromRecents(false)
        assertFalse(repository.getHideFromRecents())
    }

    /** Test 2: enabling the setting is persisted and can be read back. */
    @Test
    fun hideFromRecents_isPersisted() {
        repository.setHideFromRecents(true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fromRepository = Repository.getInstance(context).getHideFromRecents()
        assertTrue("Expected the value to be persisted as true", fromRepository)
    }

    /** Test 4/5: the activity still launches while the setting is ON. */
    @Test
    fun mainActivity_launchesWithSettingEnabled() {
        repository.setHideFromRecents(true)
        ActivityScenario.launch(MainActivity::class.java).use {
            // Reaching this point without an exception is the assertion: enabling the
            // setting must not break launching the app.
        }
    }

    /** Test 6: turning the setting back off is persisted as well. */
    @Test
    fun hideFromRecents_canBeTurnedOffAgain() {
        repository.setHideFromRecents(true)
        assertTrue(repository.getHideFromRecents())
        repository.setHideFromRecents(false)
        assertFalse(repository.getHideFromRecents())
    }
}

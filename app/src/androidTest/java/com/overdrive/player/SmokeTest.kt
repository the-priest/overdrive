package com.overdrive.player

import android.Manifest
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.overdrive.player.player.Playback
import com.overdrive.player.player.PlayerConnection
import com.overdrive.player.player.SleepTimer
import com.overdrive.player.player.songId
import com.overdrive.player.ui.MainActivity
import com.overdrive.player.ui.MainViewModel
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tour of the app on a real device/emulator: library, playback,
 * Up Next, equalizer, sleep timer, albums, search. Also captures the
 * screenshots used in the README.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SmokeTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val app get() = OverdriveApp.from(InstrumentationRegistry.getInstrumentation().targetContext)
    private val titles = TestMedia.TITLES

    /** The connected controller. Main thread only: call it inside [onMain]. */
    private fun player(): Player = PlayerConnection.controller ?: error("not connected")

    @Test
    fun fullTour() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun viewModel(): MainViewModel {
                var vm: MainViewModel? = null
                scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
                return vm!!
            }

            // --- Library ----------------------------------------------------
            waitUntil("library to load the test tracks") {
                onMain { viewModel().songs().count { it.title in titles } } == titles.size
            }
            Screenshots.take("01-library")

            // Search narrows the list down.
            onView(withId(R.id.action_search)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).perform(typeText("riff"))
            closeSoftKeyboard()
            waitUntil("search results") { onMain { viewModel().visibleSongs.value?.size } == 1 }
            Screenshots.take("02-search")
            pressBack()
            waitUntil("search to close") {
                onMain { viewModel().visibleSongs.value?.size ?: 0 } >= titles.size
            }

            // --- Playback ---------------------------------------------------
            onView(allOf(withId(R.id.songTitle), withText(titles[0]), isDisplayed())).perform(click())
            waitUntil("playback to start") { onMain { PlayerConnection.controller?.isPlaying == true } }
            onView(withId(R.id.miniPlayer)).check(matches(isDisplayed()))

            // The equalizer / boost chain must be attached to the live session.
            assertTrue("audio effects not attached", app.audioEffects.isAttached)

            // Everything after the first track is queued up, in order.
            val upNext = onMain { Playback.upNext(player()) }.map { it.item.mediaMetadata.title.toString() }
            assertEquals(titles.drop(1), upNext.filter { it in titles })
            Screenshots.take("03-library-playing")

            // --- Now Playing ------------------------------------------------
            onView(withId(R.id.miniPlayer)).perform(click())
            onView(withId(R.id.title)).check(matches(withText(titles[0])))
            SystemClockSleep.forArtwork()
            Screenshots.take("04-now-playing")

            onView(withId(R.id.favorite)).perform(click())
            val songId = onMain { player().currentMediaItem!!.songId }
            assertTrue("favorite not saved", app.prefs.isFavorite(songId))

            onView(withId(R.id.boost)).perform(click())
            assertEquals(150, app.prefs.volumeBoost)
            onView(withId(R.id.boost)).perform(click())
            assertEquals(100, app.prefs.volumeBoost)

            // --- Up Next ----------------------------------------------------
            onView(withId(R.id.upNextCard)).perform(click())
            onView(withText(R.string.queue_now_playing)).check(matches(isDisplayed()))
            Screenshots.take("05-up-next")
            onView(allOf(withId(R.id.title), withText(titles[2]))).perform(click())
            waitUntil("jump to the tapped track") {
                onMain { player().currentMediaItem?.mediaMetadata?.title?.toString() } == titles[2]
            }
            pressBack()

            // --- Sleep timer ------------------------------------------------
            onView(withId(R.id.sleep)).perform(click())
            onView(withText("15 minutes")).inRoot(isDialog()).perform(click())
            waitUntil("sleep timer to start") {
                onMain { SleepTimer.state.value is SleepTimer.State.Counting }
            }
            Screenshots.take("06-now-playing-sleep")
            onMain { SleepTimer.cancel() }

            // --- Equalizer --------------------------------------------------
            onView(withId(R.id.equalizer)).perform(click())
            onView(withText(R.string.preset_metal)).perform(click())
            assertTrue("EQ not enabled by preset", app.prefs.equalizerEnabled)
            assertTrue("Metal curve not stored", app.prefs.bandLevel(0) > 0)
            Screenshots.take("07-equalizer")
            pressBack()
            pressBack() // back to the library

            // --- Albums & album page ----------------------------------------
            onView(withText(R.string.tab_albums)).perform(click())
            Screenshots.take("08-albums")
            onView(allOf(withId(R.id.albumName), isDisplayed())).perform(click())
            onView(withId(R.id.headerTitle)).check(matches(isDisplayed()))
            Screenshots.take("09-album")
            onView(withId(R.id.shuffleButton)).perform(click())
            waitUntil("shuffle play") {
                onMain { player().shuffleModeEnabled && player().isPlaying }
            }
            pressBack()

            // --- Favorites --------------------------------------------------
            onView(withText(R.string.tab_favorites)).perform(click())
            onView(allOf(withId(R.id.songTitle), withText(titles[0]), isDisplayed()))
                .check(matches(isDisplayed()))
            Screenshots.take("10-favorites")

            onMain { player().pause() }
        }
    }

    private object SystemClockSleep {
        /** Gives the cover art / palette tint a moment to settle. */
        fun forArtwork() = android.os.SystemClock.sleep(1200)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun seedMedia() {
            TestMedia.ensureTracks(InstrumentationRegistry.getInstrumentation().targetContext)
        }
    }
}

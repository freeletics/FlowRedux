package com.freeletics.rxredux

import android.content.Intent
import android.support.test.espresso.Espresso
import android.support.test.espresso.ViewAction
import android.support.test.espresso.action.GeneralLocation
import android.support.test.espresso.action.GeneralSwipeAction
import android.support.test.espresso.action.Press
import android.support.test.espresso.action.Swipe
import android.support.test.espresso.action.ViewActions
import android.support.test.espresso.contrib.RecyclerViewActions
import android.support.test.espresso.matcher.ViewMatchers
import android.support.test.rule.ActivityTestRule
import android.support.test.rule.GrantPermissionRule
import android.support.test.runner.AndroidJUnit4
import android.support.v7.widget.RecyclerView
import com.freeletics.rxredux.businesslogic.pagination.PaginationStateMachine
import io.reactivex.Observable
import io.victoralbertos.device_animation_test_rule.DeviceAnimationTestRule
import okhttp3.mockwebserver.MockWebServer
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule


@RunWith(AndroidJUnit4::class)
class PopularRepositoriesActivityTest {

    @get:Rule
    val activityTestRule = ActivityTestRule(PopularRepositoriesActivity::class.java, false, false)

    @get:Rule
    val permission = GrantPermissionRule.grant(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

    companion object {
        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()

        @get:ClassRule
        @JvmStatic
        var deviceAnimationTestRule = DeviceAnimationTestRule()
    }

    @Test
    fun runTests() {
        // Setup test environment
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())

        PopularRepositoriesSpec(
            screen = AndroidScreen(activityTestRule),
            stateHistory = StateHistory(AndroidStateRecorder()),
            config = ScreenConfig(mockWebServer = MockWebServer().setupForHttps())
        ).runTests()
    }

    class AndroidScreen(
        private val activityRule: ActivityTestRule<PopularRepositoriesActivity>
    ) : Screen {
        override fun scrollToEndOfList() {
            Espresso
                .onView(ViewMatchers.withId(R.id.recyclerView))
                .perform(
                    RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(
                        RecordingPopularRepositoriesViewBinding.INSTANCE.lastPositionInAdapter() - 1
                    )
                )

            Espresso
                .onView(ViewMatchers.withId(R.id.recyclerView))
                .perform(swipeFromBottomToTop())

        }

        override fun retryLoadingFirstPage() {
            Espresso.onView(ViewMatchers.withId(R.id.error))
                .perform(ViewActions.click())
        }

        override fun loadFirstPage() {
            activityRule.launchActivity(Intent())
        }

        private fun swipeFromBottomToTop(): ViewAction {
            return GeneralSwipeAction(
                Swipe.FAST, GeneralLocation.BOTTOM_CENTER,
                GeneralLocation.TOP_CENTER, Press.FINGER
            )
        }
    }

    inner class AndroidStateRecorder : StateRecorder {
        override fun renderedStates(): Observable<PaginationStateMachine.State> =
            RecordingPopularRepositoriesViewBinding.INSTANCE.recordedStates
    }
}

package com.github.arhor.journey

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe

import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun `useAppContext should return journey package name when target context is requested`() {
        // Given
        val expectedPackageName = "com.github.arhor.journey"

        // When
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Then
        appContext.packageName shouldBe expectedPackageName
    }
}

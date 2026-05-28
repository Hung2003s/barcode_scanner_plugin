package com.pda_scanner_lmhung.barcode_scanner_plugin

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.mockito.Mockito
import kotlin.test.Test

/*
 * This demonstrates a simple unit test of the Kotlin portion of this plugin's implementation.
 *
 * Once you have built the plugin's example app, you can run these tests from the command
 * line by running `./gradlew testDebugUnitTest` in the `example/android/` directory, or
 * you can run them directly from IDEs that support JUnit such as Android Studio.
 */

internal class BarcodeScannerPluginTest {
    @Test
    fun onMethodCall_startCameraScan_returnsNotImplementedWhenNoActivity() {
        val plugin = BarcodeScannerPlugin()

        val call = MethodCall("startCameraScan", null)
        val mockResult: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)
        plugin.onMethodCall(call, mockResult)

        Mockito.verify(mockResult).error("NO_ACTIVITY", "Plugin không tìm thấy Activity hiện tại để khởi chạy Camera", null)
    }
}

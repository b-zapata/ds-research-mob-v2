package com.mindful.android.helpers

import android.util.Log
import com.mindful.android.AppConstants
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Helper to invoke Flutter methods from Android services
 * Singleton that stores the FlutterEngine's method channel
 */
object FlutterMethodChannelHelper {
    private const val TAG = "Mindful.FlutterMethodChannelHelper"
    private var methodChannel: MethodChannel? = null

    /**
     * Initialize with FlutterEngine (called from MainActivity)
     */
    fun initialize(flutterEngine: FlutterEngine) {
        methodChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            AppConstants.FLUTTER_METHOD_CHANNEL_FG
        )
        Log.d(TAG, "Method channel initialized")
    }

    /**
     * Invoke a Flutter method and return the result
     * Returns null if method channel not initialized or call fails
     * Note: This should be called from a background thread as it blocks
     */
    fun invokeMethod(method: String, arguments: Map<String, Any>? = null): Map<String, Any>? {
        val channel = methodChannel ?: run {
            Log.w(TAG, "Method channel not initialized, cannot invoke $method")
            return null
        }

        try {
            val future = CompletableFuture<Map<String, Any>?>()
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            
            // Post to main thread to invoke method channel
            handler.post {
                channel.invokeMethod(
                    method,
                    arguments,
                    object : io.flutter.plugin.common.MethodChannel.Result {
                        override fun success(result: Any?) {
                            @Suppress("UNCHECKED_CAST")
                            future.complete(result as? Map<String, Any>?)
                        }

                        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                            Log.e(TAG, "Error invoking $method: $errorCode - $errorMessage")
                            future.complete(null)
                        }

                        override fun notImplemented() {
                            Log.w(TAG, "Method $method not implemented in Flutter")
                            future.complete(null)
                        }
                    }
                )
            }

            // Wait up to 5 seconds for response (blocks current thread)
            return future.get(5, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            Log.e(TAG, "Timeout invoking $method (waited 5 seconds)")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Exception invoking $method", e)
            return null
        }
    }
}

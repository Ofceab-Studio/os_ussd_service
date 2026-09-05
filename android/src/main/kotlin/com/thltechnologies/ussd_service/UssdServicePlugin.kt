package com.thltechnologies.ussd_service

import android.Manifest.permission
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.concurrent.CompletableFuture

class UssdServicePlugin : FlutterPlugin, MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var ussdSessionUnique: UssdSessionUnique

    companion object {
        private const val CHANNEL_NAME = "com.thltechnologies.ussd_service/plugin_channel"
        private var methodChannel: MethodChannel? = null
        private val handler = Handler(Looper.getMainLooper())

        fun onUssdResult(result: String) {
            handler.post {
                println("UssdServicePlugin: Sending USSD result to Flutter: $result")
                methodChannel?.invokeMethod("onUssdMessageReceived", result)
            }
        }

        private fun setMethodChannel(channel: MethodChannel) {
            methodChannel = channel
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        initialize(flutterPluginBinding.applicationContext, flutterPluginBinding.binaryMessenger)
    }

    private fun initialize(context: Context, messenger: BinaryMessenger) {
        this.context = context
        this.channel = MethodChannel(messenger, CHANNEL_NAME)
        this.channel.setMethodCallHandler(this)
        setMethodChannel(channel)
        this.ussdSessionUnique = UssdSessionUnique(context)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        methodChannel = null
        ussdSessionUnique.dispose()
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "makeRequest" -> handleMakeRequest(call, result)
            "getSimCards" -> ussdSessionUnique.getSimCards(result)
            else -> result.notImplemented()
        }
    }

    // Original silent request method (without Accessibility)
    private fun handleMakeRequest(call: MethodCall, result: Result) {
        try {
            val subscriptionId = call.argument<Int>("subscriptionId")
            if (subscriptionId == null) {
                result.error("ussd_plugin_incorrect__parameters", "Incorrect parameter type: `subscriptionId` must be an int", null)
                return
            }
            if (subscriptionId < 0) {
                result.error("ussd_plugin_incorrect__parameters", "Incorrect parameter value: `subscriptionId` must be >= 0", null)
                return
            }
            val code = call.argument<String>("code")
            if (code == null) {
                result.error("ussd_plugin_incorrect__parameters", "Incorrect parameter type: `code` must be a String", null)
                return
            }
            if (code.isEmpty()) {
                result.error("ussd_plugin_incorrect__parameters", "Incorrect parameter value: `code` must not be an empty string", null)
                return
            }

            makeSilentRequest(subscriptionId, code).exceptionally { e ->
                result.error("ussd_plugin_ussd_execution_failure", e.message ?: e.toString(), null)
                null
            }.thenAccept { res ->
                if (res != null) {
                    result.success(res)
                }
            }
        } catch (e: Exception) {
            result.error("unknown_exception", e.message ?: e.toString(), null)
        }
    }

    private fun makeSilentRequest(subscriptionId: Int, code: String): CompletableFuture<String> {
        if (ContextCompat.checkSelfPermission(context, permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            val future = CompletableFuture<String>()
            future.completeExceptionally(Exception("CALL_PHONE permission missing"))
            return future
        }
        val future = CompletableFuture<String>()
        val callback = object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) {
                future.complete(response.toString())
            }

            override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) {
                val error = when (failureCode) {
                    TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "USSD_ERROR_SERVICE_UNAVAIL"
                    TelephonyManager.USSD_RETURN_FAILURE -> "USSD_RETURN_FAILURE"
                    else -> "unknown error"
                }
                future.completeExceptionally(Exception(error))
            }
        }

        try {
            val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val simManager = manager.createForSubscriptionId(subscriptionId)
            simManager.sendUssdRequest(code, callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
        return future
    }
}

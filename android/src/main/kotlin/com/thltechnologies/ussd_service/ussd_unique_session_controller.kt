package com.thltechnologies.ussd_service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class UssdSessionUnique(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun getSlotIndexFromSubscriptionId(subscriptionId: Int): Int {
        if (subscriptionId == -1) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val info = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                if (info != null) {
                    return info.simSlotIndex
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        }
        return 0
    }

    fun sendUssdRequest(ussdCode: String, subscriptionId: Int, result: MethodChannel.Result) {
        val isNonStandard = ussdCode.startsWith("#") || ussdCode.indexOf('#') != ussdCode.lastIndexOf('#')
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isNonStandard) {
            sendUssdRequestModern(ussdCode, subscriptionId, result)
        } else {
            val slotIndex = getSlotIndexFromSubscriptionId(subscriptionId)
            sendUssdRequestLegacy(ussdCode, slotIndex, result)
        }
    }

    private fun sendUssdRequestModern(ussdCode: String, subscriptionId: Int, result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            result.error("UNSUPPORTED_API", "This method requires Android 8.0+", null)
            return
        }

        val telephonyManager = if (subscriptionId != -1) {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .createForSubscriptionId(subscriptionId)
        } else {
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }

        val callback = object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) {
                result.success(response.toString())
            }

            override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) {
                val errorMessage = when (failureCode) {
                    TelephonyManager.USSD_RETURN_FAILURE -> "USSD request failed"
                    TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "USSD service unavailable"
                    else -> "Unknown error occurred (code: $failureCode)"
                }
                result.error("USSD_FAILED", errorMessage, null)
            }
        }

        scope.launch {
            try {
                telephonyManager.sendUssdRequest(ussdCode, callback, Handler(Looper.getMainLooper()))
            } catch (e: SecurityException) {
                result.error("PERMISSION_DENIED", "Permission denied: ${e.message}", null)
            } catch (e: Exception) {
                result.error("UNEXPECTED_ERROR", "Unexpected error: ${e.message}", null)
            }
        }
    }

    private fun sendUssdRequestLegacy(ussdCode: String, simSlot: Int, result: MethodChannel.Result) {
        try {
            val encodedHash = Uri.encode("#")
            val formattedCode = ussdCode.replace("#", encodedHash)
            val uri = Uri.parse("tel:$formattedCode")
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("com.android.phone.force.slot", true)
                putExtra("Cdma_Supp", true)
            }
            
            val slotKeys = arrayOf(
                "extra_asus_dial_use_dualsim",
                "com.android.phone.extra.slot",
                "slot", "simslot", "sim_slot",
                "Subscription", "phone",
                "com.android.phone.DialingMode",
                "simSlot", "slot_id", "simId",
                "simnum", "phone_type", "slotId", "slotIdx"
            )
            for (key in slotKeys) {
                intent.putExtra(key, simSlot)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager?
                telecomManager?.let {
                    val phoneAccounts = it.callCapablePhoneAccounts
                    if (phoneAccounts.size > simSlot && simSlot >= 0) {
                        intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", phoneAccounts[simSlot])
                    }
                }
            }
            
            context.startActivity(intent)
            result.success("USSD_INITIATED_LEGACY")
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "CALL_PHONE permission required: ${e.message}", null)
        } catch (e: Exception) {
            result.error("LEGACY_USSD_ERROR", "Failed to initiate USSD: ${e.message}", null)
        }
    }

    fun getSimCards(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            result.error("UNSUPPORTED_API", "This feature requires Android 5.1+", null)
            return
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

            if (activeSubscriptionInfoList != null && activeSubscriptionInfoList.isNotEmpty()) {
                val simCards = activeSubscriptionInfoList.map { subscriptionInfo ->
                    val simCard = mutableMapOf<String, Any?>(
                        "subscriptionId" to subscriptionInfo.subscriptionId,
                        "displayName" to subscriptionInfo.displayName?.toString(),
                        "carrierName" to subscriptionInfo.carrierName?.toString(),
                        "number" to subscriptionInfo.number,
                        "slotIndex" to subscriptionInfo.simSlotIndex,
                        "countryIso" to subscriptionInfo.countryIso
                    )
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        simCard["carrierId"] = subscriptionInfo.carrierId
                        simCard["isEmbedded"] = subscriptionInfo.isEmbedded
                    }
                    
                    try {
                        simCard["iccId"] = subscriptionInfo.iccId
                    } catch (e: SecurityException) {
                        simCard["iccId"] = null
                    }
                    
                    simCard
                }
                result.success(simCards)
            } else {
                result.success(emptyList<Map<String, Any?>>())
            }
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "READ_PHONE_STATE permission required: ${e.message}", null)
        } catch (e: Exception) {
            result.error("SIM_CARDS_ERROR", "Failed to get SIM cards: ${e.message}", null)
        }
    }

    fun dispose() {
        scope.cancel()
    }
}

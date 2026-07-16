# thl_ussd_service

A Flutter plugin to make silent USSD requests and read their responses, using Android's native [sendUssdRequest](https://developer.android.com/reference/android/telephony/TelephonyManager.html#sendUssdRequest(java.lang.String,%20android.telephony.TelephonyManager.UssdResponseCallback,%20android.os.Handler)) method. 

This is an updated and modernized fork of the original `ussd_service` package, fully compatible with **Dart 3**, recent **Flutter** versions, and **Android Gradle Plugin (AGP) 8.0+ / 9.0+**.

*Note: iOS is not supported.*

---

## Installation

Add `thl_ussd_service` as a dependency in your `pubspec.yaml`:

```yaml
dependencies:
  thl_ussd_service: ^1.0.0
```

### Android Manifest Permissions
Ensure that your `AndroidManifest.xml` (located in `android/app/src/main/AndroidManifest.xml`) includes the `CALL_PHONE` permission:

```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
```

---

## Usage

Before making a USSD request with this plugin, you must:

1. **Request Permissions:** Make sure the user has granted access to phone calls, for example using the [permission_handler](https://pub.dev/packages/permission_handler) package.
2. **Retrieve Subscription ID:** Retrieve the [SIM card subscription ID](https://developer.android.com/reference/android/telephony/SubscriptionInfo#getSubscriptionId()), for example using the [sim_data_plus](https://pub.dev/packages/sim_data_plus) or [flutter_sim_data](https://pub.dev/packages/flutter_sim_data) packages.

### Example Code

```dart
import 'package:flutter/services.dart';
import 'package:thl_ussd_service/ussd_service.dart';

Future<void> makeMyRequest() async {
  int subscriptionId = 1; // Retrieve this using a SIM card data plugin
  String code = "*#21#";   // Your USSD code
  
  try {
    String ussdResponseMessage = await UssdService.makeRequest(
      subscriptionId,
      code,
      const Duration(seconds: 10), // Optional timeout - default is 10 seconds
    );
    print("Success! Response message: $ussdResponseMessage");
  } on PlatformException catch (e) {
    print("Execution failed! Code: ${e.code} - Message: ${e.message}");
  }
}
```

---

## Interactive / Multi-step USSD Sessions

Android's native `sendUssdRequest` API **does not support interactive or multi-step USSD sessions** (where you input options in menus). Consequently, this plugin does not support them either.

However, many mobile carriers allow you to perform multi-step operations by appending options directly within a single request using the format:

```text
*firstCode*secondOption*thirdOption#
```
For example: `*123*1*2#` instead of dialing `*123#`, waiting for a menu, entering `1`, and then entering `2`.


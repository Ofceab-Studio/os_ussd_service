# thl_ussd_service

A Flutter plugin to make USSD requests and manage multi-step USSD sessions on Android. 

This plugin offers two distinct execution modes:
1. **Silent Mode (TelephonyManager):** Performs silent requests in the background. Invisible to the user, requires no special settings, but is subject to strict Android OS syntax limitations (only standard codes like `*100#`).
2. **Interactive/Accessibility Mode (AccessibilityService):** Simulates dialer actions to process any custom formatting (like `#101#451#`) and automates multi-step interactive menus (e.g. choosing menu options sequentially). Requires the user to enable Accessibility settings.

*Note: iOS is not supported.*

---

## Installation

Add `thl_ussd_service` as a dependency in your `pubspec.yaml`:

```yaml
dependencies:
  thl_ussd_service: ^0.2.1
```

### Android Manifest & Permissions
Ensure that your `AndroidManifest.xml` includes the `CALL_PHONE` permission:

```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
```

*Note: The plugin automatically merges the other necessary permissions (`READ_PHONE_STATE`, `SYSTEM_ALERT_WINDOW`, `BIND_ACCESSIBILITY_SERVICE`, `FOREGROUND_SERVICE`) into your final merged manifest.*

---

## Usage

### Mode 1: Silent Requests (Background, no Accessibility required)

Best for standard USSD codes where you only need a single silent response. Android 8.0+ is required.

```dart
import 'package:flutter/services.dart';
import 'package:thl_ussd_service/thl_ussd_service.dart';

Future<void> makeSilentRequest() async {
  int subscriptionId = 1; // Retrieve this using a SIM card data plugin or UssdService.getSimCards()
  String code = "*100#";
  
  try {
    String responseMessage = await UssdService.makeRequest(
      subscriptionId,
      code,
      const Duration(seconds: 10), // Optional timeout
    );
    print("Success! Response message: $responseMessage");
  } on PlatformException catch (e) {
    print("Failed! Code: ${e.code} - Message: ${e.message}");
  }
}
```

---

### Mode 2: Interactive Requests (Dialer-based, requires Accessibility)

Best for non-standard USSD codes (like `#101#451#`) that Android's native silent API rejects.

Before calling this, you must check and prompt the user to enable the Accessibility Service for your app.

```dart
import 'package:flutter/services.dart';
import 'package:thl_ussd_service/thl_ussd_service.dart';

Future<void> makeInteractiveRequest() async {
  // 1. Verify accessibility permission
  if (!await UssdService.isAccessibilityEnabled()) {
    await UssdService.openAccessibilitySettings();
    print("Please enable the Accessibility service for our app.");
    return;
  }

  // 2. Execute
  int subscriptionId = 1;
  String code = "#101#451#";
  
  try {
    String? result = await UssdService.sendUssdRequest(
      ussdCode: code,
      subscriptionId: subscriptionId,
    );
    print("Request finished. Result: $result");
  } on PlatformException catch (e) {
    print("Error: ${e.message}");
  }
}
```

---

### Mode 3: Multi-step Automation (requires Accessibility & Overlay permissions)

Automatically walks through multi-step interactive menus by sending option responses sequentially.

```dart
import 'package:thl_ussd_service/thl_ussd_service.dart';

void runMultiStepSession() async {
  // 1. Verify Accessibility
  if (!await UssdService.isAccessibilityEnabled()) {
    await UssdService.openAccessibilitySettings();
    return;
  }

  // 2. Verify Overlay Draw Permission (optional, to hide system gray dialogs)
  if (!await UssdService.isOverlayPermissionGranted()) {
    await UssdService.openOverlaySettings();
    return;
  }

  // 3. Register real-time message listener
  UssdService.setUssdMessageListener((message) {
    print("Step response: $message");
  });

  // 4. Run session
  try {
    await UssdService.multisessionUssd(
      code: '#101#',
      slotIndex: 0, // SIM slot index (0 or 1)
      options: ["1", "2", "3"], // Option inputs for menus sequentially
      overlayMessage: "Processing automatic options. Please wait...",
    );
    print("Session completed successfully.");
  } finally {
    // 5. Always clean up listener when done
    UssdService.removeUssdMessageListener();
  }
}
```

---

## Utility APIs

- `UssdService.getSimCards()`: Retrieves a list of active SIM card details (slotIndex, subscriptionId, displayName, carrierName).
- `UssdService.isAccessibilityEnabled()` / `UssdService.openAccessibilitySettings()`: Checks or prompts to enable accessibility.
- `UssdService.isOverlayPermissionGranted()` / `UssdService.openOverlaySettings()`: Checks or prompts to enable drawing over other apps.
- `UssdService.cancelSession()`: Cancels an active interactive session.



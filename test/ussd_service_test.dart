import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:thl_ussd_service/thl_ussd_service.dart';

void main() {
  const MethodChannel channel =
      MethodChannel('com.thltechnologies.ussd_service/plugin_channel');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('makeRequest', () async {
    expect(await UssdService.makeRequest(1, "*123#"), '42');
  });
}


import 'package:flutter/services.dart';

class NativeSupport {
  static const MethodChannel _channel = MethodChannel('native_support');

  static Future<bool> copyContentUriToFile(String uri, String destPath) async {
    try {
      final bool? result = await _channel.invokeMethod('copyContentUriToFile', {
        'uri': uri,
        'destPath': destPath,
      });
      return result ?? false;
    } catch (e) {
      print('NativeSupport Error: $e');
      return false;
    }
  }
}

import 'package:flutter/services.dart';

import 'models.dart';

export 'models.dart';

const _pluginNamespace = 'com.ghosten.player/logger';

class Logger {
  /// The method channel used to interact with the native platform.
  static const _methodChannel = MethodChannel(_pluginNamespace);

  static Future<LogPage> logQueryPage(int limit, int? cursor, String? filename) async {
    final dynamic data = await _methodChannel.invokeMethod('logQueryPage', {
      'limit': limit,
      'cursor': cursor,
      'filename': filename,
    });
    return LogPage.fromJson(data);
  }
}

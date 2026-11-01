import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../player.dart';
import 'player_platform_interface.dart';

class PlayerSpeed {
  const PlayerSpeed({required this.value, required this.text});

  final String text;
  final double value;
}

const playerSpeedList = [
  PlayerSpeed(text: '0.25x', value: 0.25),
  PlayerSpeed(text: '0.5x', value: 0.5),
  PlayerSpeed(text: '0.75x', value: 0.75),
  PlayerSpeed(text: '1.0x', value: 1),
  PlayerSpeed(text: '1.25x', value: 1.25),
  PlayerSpeed(text: '1.5x', value: 1.5),
  PlayerSpeed(text: '2.0x', value: 2),
  PlayerSpeed(text: '3.0x', value: 3),
  PlayerSpeed(text: '5.0x', value: 5),
];

enum ControlsStreamStatus { show, showInfinite, hide }

class PlayerPlatformView extends StatefulWidget {
  const PlayerPlatformView({
    super.key,
    this.initialized,
    this.autoPip = false,
    required this.playerType,
    this.initializeFailed,
    this.mpvVersion,
  });

  final VoidCallback? initialized;
  final Function(PlatformException)? initializeFailed;
  final bool autoPip;
  final PlayerType playerType;
  final String? mpvVersion;

  @override
  State<PlayerPlatformView> createState() => _PlayerPlatformViewState();
}

class _PlayerPlatformViewState extends State<PlayerPlatformView> {
  @override
  void initState() {
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) {
        return;
      }
      final box = context.findRenderObject()! as RenderBox;
      final Offset offset = box.globalToLocal(Offset.zero);
      final double devicePixelRatio = MediaQuery.of(context).devicePixelRatio;
      final String language = Localizations.localeOf(context).languageCode;
      final SharedPreferences prefs = await SharedPreferences.getInstance();
      if (!context.mounted) {
        return;
      }
      try {
        await PlayerPlatform.instance.init({
          'type': widget.playerType.name,
          'mpvVersion': widget.mpvVersion,
          'language': language,
          'width': (box.size.width * devicePixelRatio).round(),
          'height': (box.size.height * devicePixelRatio).round(),
          'top': (offset.dy * -1 * devicePixelRatio).round(),
          'left': (offset.dx * -1 * devicePixelRatio).round(),
          'autoPip': widget.autoPip,
          'extensionRendererMode': PlayerConfig.getExtensionRendererMode(prefs),
          'enableDecoderFallback': PlayerConfig.getEnableDecoderFallback(prefs),
          'subtitleStyle': PlayerConfig.getSubtitleSettings(prefs),
        });
        widget.initialized?.call();
      } on PlatformException catch (e) {
        widget.initializeFailed?.call(e);
      }
    });
    super.initState();
  }

  @override
  void dispose() {
    PlayerPlatform.instance.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return const SizedBox.expand();
  }
}

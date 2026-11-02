import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
    required this.extensionRendererMode,
    required this.enableDecoderFallback,
    required this.subtitleStyle,
    this.preferredSubtitleLanguage,
    this.preferredAudioLanguage,
    this.controls,
  });

  final Widget? controls;
  final VoidCallback? initialized;
  final Function(PlatformException)? initializeFailed;
  final bool autoPip;
  final PlayerType playerType;
  final String? mpvVersion;
  final int extensionRendererMode;
  final String? preferredSubtitleLanguage;
  final String? preferredAudioLanguage;
  final bool enableDecoderFallback;
  final List<int> subtitleStyle;

  @override
  State<PlayerPlatformView> createState() => _PlayerPlatformViewState();
}

class _PlayerPlatformViewState extends State<PlayerPlatformView> {
  late final creationParams = <String, dynamic>{
    'type': widget.playerType.name,
    'mpvVersion': widget.mpvVersion,
    'preferredSubtitleLanguage': widget.preferredSubtitleLanguage,
    'preferredAudioLanguage': widget.preferredAudioLanguage,
    'autoPip': widget.autoPip,
    'extensionRendererMode': widget.extensionRendererMode,
    'enableDecoderFallback': widget.enableDecoderFallback,
    'subtitleStyle': widget.subtitleStyle,
    'backgroundColor': Theme.of(context).scaffoldBackgroundColor.toARGB32(),
  };
  bool _loaded = false;

  @override
  void initState() {
    Future.microtask(() async {
      try {
        await PlayerPlatform.instance.create(creationParams);
        await WidgetsBinding.instance.endOfFrame;
        if (!context.mounted) {
          return;
        }
        widget.initialized?.call();
        setState(() => _loaded = true);
      } on PlatformException catch (e) {
        if (!context.mounted) {
          return;
        }
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
    return _loaded
        ? widget.controls ?? const SizedBox.expand()
        : ColoredBox(color: Theme.of(context).scaffoldBackgroundColor, child: const SizedBox.expand());
  }
}

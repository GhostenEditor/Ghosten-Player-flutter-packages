enum PlaybackStatusEvent { start, progress, stop }

enum PlayerType {
  media3,
  mpv;

  static PlayerType fromString(String? str) {
    return PlayerType.values.firstWhere((element) => element.name == str, orElse: () => PlayerType.media3);
  }
}

enum PlaybackState {
  idle,
  buffering,
  ready,
  ended;

  static PlaybackState fromInt(int index) {
    return PlaybackState.values[index - 1];
  }
}

enum TrackType {
  none,
  unknown,
  default_,
  audio,
  video,
  text,
  image,
  metadata,
  cameraMotion;

  static TrackType fromInt(int index) {
    return TrackType.values[index + 2];
  }
}

enum AspectRatioType {
  auto,
  a16_9,
  a4_3,
  a1_1;

  double? value() {
    return switch (this) {
      AspectRatioType.auto => null,
      AspectRatioType.a16_9 => 1.778,
      AspectRatioType.a4_3 => 1.333,
      AspectRatioType.a1_1 => 1.0,
    };
  }

  String label() {
    return switch (this) {
      AspectRatioType.auto => 'Auto',
      AspectRatioType.a16_9 => '16 / 9',
      AspectRatioType.a4_3 => '4 / 3',
      AspectRatioType.a1_1 => '1 / 1',
    };
  }
}

enum ResizeMode {
  fit,
  fixedWidth,
  fixedHeight,
  fill,
  zoom;

  String label() {
    return switch (this) {
      ResizeMode.fit => 'Fit',
      ResizeMode.fixedWidth => 'Fixed Width',
      ResizeMode.fixedHeight => 'Fixed Height',
      ResizeMode.fill => 'Fill',
      ResizeMode.zoom => 'Zoom',
    };
  }
}

enum SubtitleMimeType { xml, vtt, ass, srt }

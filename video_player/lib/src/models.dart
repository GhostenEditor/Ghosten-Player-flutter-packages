// ignore_for_file: avoid_dynamic_calls

import 'dart:ui';

import 'package:collection/collection.dart';
import 'package:equatable/equatable.dart';

import 'enums.dart';

class MediaTrack {
  MediaTrack.fromJson(dynamic json)
    : id = json['id'],
      selected = json['selected'],
      supported = json['supported'],
      label = json['label'],
      mimeType = json['mimeType'],
      rate = json['rate'],
      averageBitrate = json['averageBitrate'],
      name = json['name'],
      type = json['type'];
  String? id;
  bool selected;
  bool supported;
  String? label;
  String? mimeType;
  int? rate;
  int? averageBitrate;
  String? name;
  String type;
}

class MediaTrackGroup {
  MediaTrackGroup({required this.video, required this.sub, required this.audio}) {
    selectedVideo = video.firstWhereOrNull((e) => e.selected)?.id;
    selectedAudio = audio.firstWhereOrNull((e) => e.selected)?.id;
    selectedSub = sub.firstWhereOrNull((e) => e.selected)?.id;
  }

  MediaTrackGroup.empty() : video = [], sub = [], audio = [];

  MediaTrackGroup.fromTracks(List<MediaTrack> tracks)
    : audio = tracks.where((track) => track.type == 'audio').toList(),
      video = tracks.where((track) => track.type == 'video').toList(),
      sub = tracks.where((track) => track.type == 'sub').toList(),
      selectedVideo = tracks.firstWhereOrNull((track) => track.type == 'video' && track.selected)?.id,
      selectedAudio = tracks.firstWhereOrNull((track) => track.type == 'audio' && track.selected)?.id,
      selectedSub = tracks.firstWhereOrNull((track) => track.type == 'sub' && track.selected)?.id;
  final List<MediaTrack> video;
  final List<MediaTrack> audio;
  final List<MediaTrack> sub;

  dynamic selectedVideo;
  dynamic selectedAudio;
  dynamic selectedSub;
}

class MediaChange {
  MediaChange.fromJson(Map<String, dynamic> json)
    : index = json['index'],
      position = Duration(milliseconds: json['position']);
  final int index;
  final Duration position;
}

class MediaInfo {
  MediaInfo.fromJson(dynamic json)
    : videoCodecs = json['videoCodecs'],
      videoMime = json['videoMime'],
      videoFPS = json['videoFPS'],
      videoSize = json['videoSize'],
      videoBitrate = json['videoBitrate'],
      audioCodecs = json['audioCodecs'],
      audioMime = json['audioMime'],
      audioBitrate = json['audioBitrate'];
  final String? videoCodecs;
  final String? videoMime;
  final double? videoFPS;
  final int? videoBitrate;
  final String? videoSize;
  final String? audioCodecs;
  final String? audioMime;
  final int? audioBitrate;
}

class PlaylistItem extends Equatable {
  const PlaylistItem({
    this.source,
    this.title,
    this.description,
    this.poster,
    this.url,
    this.start = Duration.zero,
    this.end = Duration.zero,
  }) : assert(source != null || url != null);

  final String? title;
  final String? description;
  final String? poster;
  final Duration start;
  final Duration end;
  final Uri? url;
  final dynamic source;

  PlaylistItem copyWith({
    String? poster,
    String? title,
    String? description,
    String? mimeType,
    Uri? url,
    Duration? start,
    Duration? end,
    dynamic source,
  }) {
    return PlaylistItem(
      poster: poster ?? this.poster,
      title: title ?? this.title,
      description: description ?? this.description,
      url: url ?? this.url,
      start: start ?? this.start,
      end: end ?? this.end,
      source: source ?? this.source,
    );
  }

  @override
  List<Object?> get props => [title, description, poster];
}

class PlayerFileInfo {
  const PlayerFileInfo({required this.url, this.subtitles, this.others});

  final Uri url;
  final List<Subtitle>? subtitles;
  final dynamic others;
}

String? parseMimeType(Uri uri) {
  if (uri.scheme == 'http' || uri.scheme == 'https') {
    final String path = uri.path;
    final String ext = path.split('.').last;
    switch (ext.toLowerCase()) {
      case 'm3u8':
        return 'application/x-mpegURL';
      case 'mp4':
      case 'mkv':
        return null;
      case 'php':
        return 'application/x-mpegURL';
      default:
        if (ext.length > 4) {
          return 'application/x-mpegURL';
        } else {
          return null;
        }
    }
  } else {
    return null;
  }
}

class Subtitle {
  const Subtitle({required this.url, required this.mimeType, this.language, this.label, this.selected = false});

  final Uri url;
  final SubtitleMimeType mimeType;
  final String? language;
  final String? label;
  final bool selected;

  Map<String, dynamic> toJson() {
    return {
      'url': url.toString(),
      'mimeType': mimeType.name,
      'language': language,
      'label': label,
      'selected': selected,
    };
  }
}

class SubtitleSettings extends Equatable {
  const SubtitleSettings({
    required this.foregroundColor,
    required this.backgroundColor,
    required this.windowColor,
    required this.edgeColor,
  });

  SubtitleSettings.fromJson(List<int> json)
    : foregroundColor = Color(json[0]),
      backgroundColor = Color(json[1]),
      windowColor = Color(json[2]),
      edgeColor = Color(json[3]);
  final Color foregroundColor;
  final Color backgroundColor;
  final Color windowColor;
  final Color edgeColor;

  List<int> toJson() {
    // ignore: deprecated_member_use
    return [foregroundColor, backgroundColor, windowColor, edgeColor].map((c) => c.value).toList();
  }

  @override
  List<Object?> get props => [foregroundColor, backgroundColor, windowColor, edgeColor];
}

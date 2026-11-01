import 'package:collection/collection.dart';

enum MediaType { movie, series, season, episode }

enum DriverType { local, webdav, alipan, quark, emby, jellyfin }

enum FileType { file, folder }

enum FuzzySearchType { movie, series, episode, cast, crew }

enum FileCategory {
  other,
  video,
  audio,
  image,
  doc;

  static FileCategory fromInt(int index) {
    return FileCategory.values.elementAt(index);
  }
}

enum MediaStatus {
  returningSeries,
  planned,
  inProduction,
  ended,
  canceled,
  pilot,
  rumored,
  postProduction,
  released,
  unknown;

  static MediaStatus fromString(String? name) {
    return switch (name) {
      'Returning Series' => MediaStatus.returningSeries,
      'Planned' => .planned,
      'In Production' => .inProduction,
      'Ended' => MediaStatus.ended,
      'Canceled' => .canceled,
      'Pilot' => .pilot,
      'Rumored' => rumored,
      'Post Production' => .postProduction,
      'Released' => MediaStatus.released,
      _ => MediaStatus.unknown,
    };
  }
}

enum LibraryType { tv, movie }

enum SkipTimeType { intro, ending }

enum SessionStatus { created, progressing, data, finished, failed }

enum SortType {
  // ignore: unused_field
  _default,
  title,
  airDate,
  createAt,
  lastPlayedTime,
}

enum PlayedStatusUpdateEventType { start, stop, progress }

enum SortDirection { asc, desc }

enum ScraperType { tmdb, nfo }

enum NetworkDiagnosticsStatus { success, fail }

enum ServerType { local, remote, emby, jellyfin }

enum DownloadTaskStatus { idle, downloading, complete, failed }

enum ScheduleTaskType { syncLibrary, scrapeLibrary }

enum ScheduleTaskStatus { idle, running, paused, completed, error }

enum ScraperBehavior { skip, chooseFirst, exact }

enum SupportedLanguages {
  auto,
  zhCn,
  enUs;

  static SupportedLanguages fromString(String? str) {
    return SupportedLanguages.values.firstWhere(
      (element) => element.name == str,
      orElse: () => SupportedLanguages.auto,
    );
  }
}

enum SubtitleType {
  srt,
  ass,
  vtt,
  xml;

  static SubtitleType? fromString(String? str) {
    if (str?.toLowerCase() == 'subrip') {
      return SubtitleType.srt;
    }
    return SubtitleType.values.firstWhereOrNull((element) => element.name == str);
  }
}

enum EventType { series, season, episode, movie, playlist, storage, library, server, subtitle }

enum EventSubType { add, update, delete }

enum ScheduleTaskEventType { syncLibrary, scrapeLibrary }

enum ScheduleTaskEventStatus { idle, update, done, error }

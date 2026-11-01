import 'package:flutter/material.dart';
import 'package:flutter_web_plugins/flutter_web_plugins.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';

import '../file_picker.dart';
import 'file_picker_platform_interface.dart';

class FilePickerWeb extends FilePickerPlatform {
  FilePickerWeb();

  static void registerWith(Registrar registrar) {
    FilePickerPlatform.instance = FilePickerWeb();
  }

  @override
  Future<String?> get externalStoragePath {
    return moviePath;
  }

  @override
  Future<String?> get moviePath {
    return Future.value();
  }

  @override
  Future<String?> get musicPath {
    return Future.value();
  }

  @override
  Future<String?> get cachePath {
    return Future.value();
  }

  @override
  Future<bool> requestStoragePermission() async => true;

  @override
  Future<T?> showFilePicker<T>(
    BuildContext context, {
    String? rootPath,
    required FilePickerType type,
    Widget? defaultTitle,
    required Widget Function(T?) titleBuilder,
    required List<Widget> actions,
    required Future<PageData<T>> Function(int) fetchData,
    required ItemWidgetBuilder<T> itemBuilder,
    required FileViewerController<T> controller,
    EdgeInsets contentPadding = .zero,
    WidgetBuilder? firstPageProgressIndicatorBuilder,
    WidgetBuilder? newPageProgressIndicatorBuilder,
    WidgetBuilder? noItemsFoundIndicatorBuilder,
    WidgetBuilder? firstPageErrorIndicatorBuilder,
  }) async {
    switch (type) {
      case FilePickerType.remote:
        return Navigator.of(context).push<T>(
          MaterialPageRoute(
            builder: (context) => FilePickerDialog(
              defaultTitle: defaultTitle,
              titleBuilder: titleBuilder,
              actions: actions,
              fetchData: fetchData,
              itemBuilder: itemBuilder,
              controller: controller,
              contentPadding: contentPadding,
              firstPageProgressIndicatorBuilder: firstPageProgressIndicatorBuilder,
              newPageProgressIndicatorBuilder: newPageProgressIndicatorBuilder,
              noItemsFoundIndicatorBuilder: noItemsFoundIndicatorBuilder,
              firstPageErrorIndicatorBuilder: firstPageErrorIndicatorBuilder,
            ),
          ),
        );
      case FilePickerType.local:
        return Future.value();
    }
  }
}

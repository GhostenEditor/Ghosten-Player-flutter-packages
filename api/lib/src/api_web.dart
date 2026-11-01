import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_web_plugins/flutter_web_plugins.dart';
import 'package:msgpack_dart/msgpack_dart.dart';

import 'api_platform_interface.dart';

class ApiWeb extends ApiPlatform {
  ApiWeb();

  static void registerWith(Registrar registrar) {
    ApiPlatform.instance = ApiWeb();
  }

  @override
  late final Client client = Client(baseUrl);

  @override
  Future<bool> initialized() async => true;

  @override
  Stream<T> streamWithCallback<T, D>(Future<dynamic> future, T Function(D) callback) async* {
    final resp = await future as ResponseBody;
    yield* resp.stream.map((event) => callback(deserialize(event) as D));
  }
}

class Client extends ApiClient {
  Client(Uri baseUrl) {
    _client =
        Dio(
            BaseOptions(
              connectTimeout: const Duration(seconds: 10),
              baseUrl: baseUrl.toString(),
              responseType: ResponseType.bytes,
              validateStatus: (status) => status != null && status >= 200 && status < 300 || status == 304,
            ),
          )
          ..interceptors.add(
            InterceptorsWrapper(
              onRequest: (options, handler) {
                final data = (options.method != 'GET' && options.data != null)
                    ? serialize((options.data as Map<String, dynamic>).values)
                    : null;
                handler.next(
                  options
                    ..contentType = 'application/msgpack'
                    ..headers.putIfAbsent(
                      'Accept-Language',
                      () => navigatorKey.currentContext != null
                          ? Localizations.localeOf(navigatorKey.currentContext!).languageCode
                          : null,
                    )
                    ..responseType = switch (options.uri.path) {
                      '/dlna/discover/cb' ||
                      '/network/diagnostics/cb' ||
                      '/storage/insert/cb' ||
                      '/schedule/task/event' => ResponseType.stream,
                      _ => ResponseType.bytes,
                    }
                    ..data = data
                    ..queryParameters.removeWhere((_, v) => v == null),
                );
              },
              onResponse: (response, handler) {
                if (response.requestOptions.responseType == ResponseType.bytes) {
                  final data = (response.data as Uint8List).isNotEmpty ? deserialize(response.data!) : null;
                  handler.next(response..data = data);
                } else {
                  handler.next(response);
                }
              },
              onError: (error, handler) {
                handler.reject(switch (error.type) {
                  DioExceptionType.connectionTimeout ||
                  DioExceptionType.sendTimeout ||
                  DioExceptionType.receiveTimeout ||
                  DioExceptionType.transformTimeout => DioException(
                    requestOptions: error.requestOptions,
                    response: Response(
                      requestOptions: error.requestOptions,
                      statusCode: 40800,
                      data: '',
                      statusMessage: error.response?.statusMessage,
                    ),
                  ),
                  DioExceptionType.badCertificate ||
                  DioExceptionType.badResponse ||
                  DioExceptionType.cancel ||
                  DioExceptionType.connectionError ||
                  DioExceptionType.unknown =>
                    error
                      ..response?.data = utf8.decode(error.response?.data)
                      ..response?.statusCode =
                          (int.tryParse(error.response?.headers.value('Error-Code') ?? '') ??
                          (error.response?.statusCode ?? 0) * 100),
                });
              },
            ),
          );
  }

  late final Dio _client;

  @override
  Future<T?> get<T>(String path, {Map<String, dynamic>? queryParameters}) {
    return _client.get<T>(path, queryParameters: queryParameters).then((resp) => resp.data);
  }

  @override
  Future<T?> post<T>(String path, {Object? data}) {
    return _client.post<T>(path, data: data).then((resp) => resp.data);
  }

  @override
  Future<T?> put<T>(String path, {Object? data}) {
    return _client.put<T>(path, data: data).then((resp) => resp.data);
  }

  @override
  Future<T?> delete<T>(String path, {Object? data}) {
    return _client.delete<T>(path, data: data).then((resp) => resp.data);
  }
}

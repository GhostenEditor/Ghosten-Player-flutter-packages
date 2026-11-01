import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import 'enums.dart';
import 'player_controller.dart';

class PlayerProgressController extends ChangeNotifier {
  PlayerProgressController(this.controller, {this.showThumbnails = false}) {
    controller.status.addListener(setStatus);
    controller.position.addListener(setPosition);
    controller.duration.addListener(setDuration);
    controller.bufferedPosition.addListener(setBuffered);
  }

  Duration duration = Duration.zero;
  Duration buffered = Duration.zero;
  Duration position = Duration.zero;
  Duration cachedPosition = Duration.zero;
  PlayerStatus status = PlayerStatus.buffering;
  bool seeking = false;
  final bool showThumbnails;
  final PlayerBaseController controller;
  ScrollController scrollController = ScrollController();

  @override
  void dispose() {
    if (entry.mounted) {
      entry.dispose();
    }
    scrollController.dispose();
    controller.status.removeListener(setStatus);
    controller.position.removeListener(setPosition);
    controller.duration.removeListener(setDuration);
    controller.bufferedPosition.removeListener(setBuffered);
    super.dispose();
  }

  late final entry = OverlayEntry(
    builder: (context) => Theme(
      data: Theme.of(context),
      child: _ThumbnailsList(
        scrollController: scrollController,
        getVideoThumbnail: controller.getVideoThumbnail,
        itemCount: (duration.inMilliseconds / 30000).ceil(),
      ),
    ),
  );

  void setStatus() {
    if (status != controller.status.value) {
      status = controller.status.value;
      notifyListeners();
    }
  }

  void setPosition() {
    if (position != controller.position.value) {
      position = controller.position.value;
      notifyListeners();
    }
  }

  void setDuration() {
    if (duration != controller.duration.value) {
      duration = controller.duration.value;
      notifyListeners();
    }
  }

  void setBuffered() {
    if (buffered != controller.bufferedPosition.value) {
      buffered = controller.bufferedPosition.value;
      notifyListeners();
    }
  }

  void startSeek(BuildContext context) {
    if (status == PlayerStatus.error || status == PlayerStatus.idle) {
      return;
    }
    if (duration == Duration.zero) {
      return;
    }
    seeking = true;
    cachedPosition = position;
    scrollController.dispose();
    scrollController = ScrollController(initialScrollOffset: calcOffset());
    notifyListeners();
    if (showThumbnails) {
      if (entry.mounted) {
        entry.remove();
      }
      Overlay.maybeOf(context)?.insert(entry);
    }
  }

  void updateSeek(BuildContext context, Duration position) {
    cachedPosition = position.clamp(Duration.zero, duration);
    notifyListeners();
    if (showThumbnails && scrollController.hasClients) {
      final double offset = calcOffset();
      if (offset != scrollController.offset) {
        scrollController.animateTo(offset, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
      }
    }
  }

  void endSeek(BuildContext context) {
    seeking = false;
    notifyListeners();
    if (showThumbnails) {
      if (entry.mounted) {
        entry.remove();
      }
    }
  }

  double calcOffset() {
    const width = 150.0;
    const gap = 10;
    final int index = (cachedPosition.inMilliseconds / 30000).floor();
    final double offset = (width + gap) * index;
    final double max = (duration.inMilliseconds / 30000 + 1).floor() * (width + gap) - gap;
    return offset.clamp(0.0, max);
  }
}

class _ThumbnailsList extends StatefulWidget {
  const _ThumbnailsList({required this.scrollController, required this.itemCount, required this.getVideoThumbnail});

  final ScrollController scrollController;
  final int itemCount;
  final Future<String?> Function(int) getVideoThumbnail;

  @override
  State<_ThumbnailsList> createState() => _ThumbnailsListState();
}

class _ThumbnailsListState extends State<_ThumbnailsList> with TickerProviderStateMixin {
  late final ScrollController _scrollController = widget.scrollController;
  late final AnimationController _animationController = AnimationController(
    duration: const Duration(seconds: 1),
    vsync: this,
  )..repeat(reverse: true);

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SizedBox(
        height: 100,
        child: Stack(
          alignment: Alignment.center,
          children: [
            ListView.separated(
              scrollCacheExtent: const ScrollCacheExtent.pixels(0),
              controller: _scrollController,
              padding: EdgeInsets.symmetric(horizontal: (MediaQuery.of(context).size.width - 150) / 2),
              scrollDirection: Axis.horizontal,
              itemBuilder: (context, index) => AspectRatio(
                aspectRatio: 1.5,
                child: FutureBuilder(
                  future: widget.getVideoThumbnail(30000 * index + 15000),
                  builder: (context, snapshot) {
                    if (snapshot.hasData) {
                      if (kIsWeb) {
                        return Image.network(
                          snapshot.data!,
                          fit: BoxFit.cover,
                          errorBuilder: (context, error, _) => ColoredBox(
                            color: Theme.of(context).colorScheme.surface,
                            child: Icon(
                              Icons.broken_image_outlined,
                              size: 40,
                              color: Theme.of(context).colorScheme.primaryContainer,
                            ),
                          ),
                        );
                      } else {
                        return Image.file(
                          File(snapshot.data!),
                          fit: BoxFit.cover,
                          errorBuilder: (context, error, _) => ColoredBox(
                            color: Theme.of(context).colorScheme.surface,
                            child: Icon(
                              Icons.broken_image_outlined,
                              size: 40,
                              color: Theme.of(context).colorScheme.primaryContainer,
                            ),
                          ),
                        );
                      }
                    } else {
                      if (snapshot.connectionState != ConnectionState.done) {
                        return AnimatedBuilder(
                          animation: _animationController,
                          builder: (context, _) => Container(
                            color: Color.lerp(
                              Theme.of(context).colorScheme.surface,
                              Theme.of(context).colorScheme.surfaceContainerHighest,
                              _animationController.value,
                            ),
                            child: Icon(
                              Icons.image_outlined,
                              size: 40,
                              color: Theme.of(context).colorScheme.primaryContainer,
                            ),
                          ),
                        );
                      } else {
                        return ColoredBox(
                          color: Theme.of(context).colorScheme.surface,
                          child: Icon(
                            Icons.broken_image_outlined,
                            size: 40,
                            color: Theme.of(context).colorScheme.primaryContainer,
                          ),
                        );
                      }
                    }
                  },
                ),
              ),
              itemCount: widget.itemCount,
              separatorBuilder: (BuildContext context, int index) => const SizedBox(width: 10),
            ),
            AspectRatio(
              aspectRatio: 1.5,
              child: Container(
                decoration: BoxDecoration(
                  border: Border.all(width: 4, color: Theme.of(context).colorScheme.primary),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class PlayerProgressView extends StatefulWidget {
  const PlayerProgressView(
    this.controller, {
    super.key,
    this.seekStart,
    this.seekEnd,
    this.thickness = 12,
    this.showLabel = true,
    this.scalable = true,
  });

  final double thickness;
  final bool showLabel;
  final bool scalable;
  final VoidCallback? seekStart;
  final Function(Duration)? seekEnd;
  final PlayerProgressController controller;

  @override
  State<PlayerProgressView> createState() => _PlayerProgressViewState();
}

class _PlayerProgressViewState extends State<PlayerProgressView> {
  late final PlayerProgressController _controller = widget.controller;

  @override
  void initState() {
    _controller.addListener(update);
    super.initState();
  }

  @override
  void dispose() {
    _controller.removeListener(update);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final child = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            excludeFromSemantics: true,
            onTapDown: (e) {
              if (!_controller.seeking) {
                _controller.startSeek(context);
                if (_controller.seeking) {
                  widget.seekStart?.call();
                }
              }
            },
            onHorizontalDragStart: (e) {
              if (!_controller.seeking) {
                _controller.startSeek(context);
                if (_controller.seeking) {
                  widget.seekStart?.call();
                }
              }
            },
            onHorizontalDragUpdate: (details) {
              if (_controller.seeking) {
                final box = context.findRenderObject()! as RenderBox;
                final double frac = details.delta.dx / box.size.width;
                _controller.updateSeek(context, _controller.cachedPosition + _controller.duration * frac);
              }
            },
            onHorizontalDragEnd: (e) {
              if (_controller.seeking) {
                widget.seekEnd?.call(_controller.cachedPosition);
                _controller.endSeek(context);
              }
            },
            onTapUp: (e) {
              if (_controller.seeking) {
                widget.seekEnd?.call(_controller.cachedPosition);
                _controller.endSeek(context);
              }
            },
            child: Padding(
              padding: EdgeInsets.symmetric(vertical: widget.showLabel ? 6 : 12),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                height: _controller.seeking ? widget.thickness / 3 * 4 : widget.thickness,
                curve: Curves.easeOutCubic,
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(1000)),
                clipBehavior: Clip.antiAlias,
                child: Stack(
                  children: [
                    Container(color: Theme.of(context).colorScheme.surface),
                    AnimatedFractionallySizedBox(
                      duration: const Duration(milliseconds: 200),
                      widthFactor: max(_controller.buffered / _controller.duration ?? 0, 0),
                      child: Container(color: Theme.of(context).colorScheme.surfaceContainerHighest),
                    ),
                    if (_controller.status == PlayerStatus.error)
                      Container(color: Theme.of(context).colorScheme.errorContainer)
                    else
                      AnimatedFractionallySizedBox(
                        duration: const Duration(milliseconds: 100),
                        widthFactor: max(
                          (_controller.seeking ? _controller.cachedPosition : _controller.position) /
                                  _controller.duration ??
                              0,
                          0,
                        ),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 100),
                          color: _controller.seeking
                              ? Theme.of(context).colorScheme.primaryFixed
                              : Theme.of(context).colorScheme.primary,
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
          if (widget.showLabel)
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(_controller.seeking ? _controller.cachedPosition.toDisplay() : _controller.position.toDisplay()),
                if (!_controller.seeking) Text(_controller.duration.toDisplay()),
                if (_controller.seeking) Text('-${(_controller.duration - _controller.cachedPosition).toDisplay()}'),
              ],
            ),
        ],
      ),
    );
    return SizedBox(
      height: widget.showLabel ? 52 : 42,
      child: Center(
        child: widget.scalable
            ? AnimatedScale(
                scale: _controller.seeking ? 1.05 : 1,
                duration: const Duration(milliseconds: 200),
                child: child,
              )
            : child,
      ),
    );
  }

  void update() {
    setState(() {});
  }
}

class PlayerProgressLabel extends StatefulWidget {
  const PlayerProgressLabel({super.key, required this.controller});

  final PlayerProgressController controller;

  @override
  State<PlayerProgressLabel> createState() => _PlayerProgressLabelState();
}

class _PlayerProgressLabelState extends State<PlayerProgressLabel> {
  @override
  void initState() {
    widget.controller.addListener(update);
    super.initState();
  }

  @override
  void dispose() {
    widget.controller.removeListener(update);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTextStyle(
      style: Theme.of(context).textTheme.labelMedium!,
      child: Row(
        children: [
          ConstrainedBox(
            constraints: const BoxConstraints(minWidth: 36),
            child: Text(
              widget.controller.seeking
                  ? widget.controller.cachedPosition.toDisplay()
                  : widget.controller.position.toDisplay(),
            ),
          ),
          const Text('-', style: TextStyle(color: Colors.transparent)),
          const Text('/'),
          Text('-', style: widget.controller.seeking ? null : const TextStyle(color: Colors.transparent)),
          ConstrainedBox(
            constraints: const BoxConstraints(minWidth: 36),
            child: Text(
              (widget.controller.seeking
                      ? widget.controller.duration - widget.controller.cachedPosition
                      : widget.controller.duration)
                  .toDisplay(),
              textAlign: TextAlign.end,
            ),
          ),
        ],
      ),
    );
  }

  void update() {
    setState(() {});
  }
}

extension on Duration {
  String toDisplay() {
    if (inHours > 0) {
      return '$inHours:${inMinutes.remainder(60).toString().padLeft(2, '0')}:${inSeconds.remainder(60).toString().padLeft(2, '0')}';
    } else {
      return '${inMinutes.remainder(60).toString().padLeft(2, '0')}:${inSeconds.remainder(60).toString().padLeft(2, '0')}';
    }
  }

  Duration clamp(Duration min, Duration max) {
    assert(min <= max);
    if (this < min) {
      return min;
    } else if (this > max) {
      return max;
    } else {
      return this;
    }
  }
}

extension DurationDivision on Duration {
  double? operator /(Duration other) {
    if (other == Duration.zero) {
      return null;
    } else {
      return inMilliseconds / other.inMilliseconds;
    }
  }
}

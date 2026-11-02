package com.ghosten.videoplayer

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.widget.FrameLayout
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

class PlayerViewPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler {
    private lateinit var activity: Activity
    private lateinit var mChannel: MethodChannel
    private lateinit var mViewChannel: MethodChannel
    private lateinit var mRootView: FrameLayout

    private var mPlayerView: PlatformView? = null
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        mChannel = MethodChannel(binding.binaryMessenger, "com.ghosten.player/player")
        mViewChannel = MethodChannel(binding.binaryMessenger, "com.ghosten.player/player_view")
        mViewChannel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        mPlayerView?.dispose()
        mPlayerView = null
    }


    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        when (call.method) {
            "create" -> {
                if (mPlayerView == null) {
                    val builder = GhostenPlayerView.Builder(activity.applicationContext, activity, mChannel)
                        .setPreferredSubtitleLanguage(call.argument("preferredSubtitleLanguage"))
                        .setPreferredAudioLanguage(call.argument("preferredAudioLanguage"))
                        .setAutoPip(call.argument("autoPip") ?: true)
                        .setSubtitleStyle(call.argument("subtitleStyle"))
                        .setBackgroundColor(call.argument<Long>("backgroundColor")?.toInt())
                    when (call.argument("type") as String?) {
                        "media3" -> {
                            mPlayerView = builder.media3Builder()
                                .setEnableDecoderFallback(call.argument("enableDecoderFallback"))
                                .setExtensionRendererMode(call.argument("extensionRendererMode"))
                                .build()
                            mRootView.addView(mPlayerView!!.view, 0)
                        }

                        "mpv" -> {
                            mPlayerView = builder.mpvBuilder()
                                .setHwdec(true)
                                .setVersion(call.argument("mpvVersion") ?: "")
                                .build()
                            mRootView.addView(mPlayerView!!.view, 0)
                        }
                    }
                }
                result.success(null)
            }

            "dispose" -> {
                if (mPlayerView != null) {
                    mPlayerView!!.dispose()
                    mRootView.removeView(mPlayerView!!.view)
                    mPlayerView = null
                }
                result.success(null)
            }

            else -> result.notImplemented()
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        mRootView = activity.findViewById(android.R.id.content)
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder().setAutoEnterEnabled(true).build()
            )
        }
    }
}

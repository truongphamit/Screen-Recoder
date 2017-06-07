package org.truongpq.screenrecorderlite;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

public class RecorderService extends Service {
    private static final int SCREEN_RECORDER_NOTIFICATION = 1;
    private static final String VIDEO_MIME_TYPE = "video/avc";

    public static MediaProjection mediaProjection;
    private WindowManager windowManager;
    private MediaCodec.BufferInfo videoBufferInfo;

    DisplayMetrics displaymetrics;

    private MediaMuxer muxer;
    private Surface inputSurface;
    private MediaCodec videoEncoder;
    private int trackIndex = -1;
    private boolean muxerStarted = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        displaymetrics = new DisplayMetrics();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displaymetrics);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title("File name:")
                .positiveText("Record")
                .positiveColor(ContextCompat.getColor(this, R.color.positive_material_dialog))
                .backgroundColor(ContextCompat.getColor(this, R.color.colorText))
                .titleColor(ContextCompat.getColor(this, R.color.content_material_dialog))
                .contentColor(ContextCompat.getColor(this, R.color.content_material_dialog))
                .input("File name", "ScreenRecorder-" + (new Date()).getTime(), new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {

                    }
                });
        final MaterialDialog dialog = builder.build();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        View positive = dialog.getActionButton(DialogAction.POSITIVE);
        final EditText editText = dialog.getInputEditText();
        positive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (editText != null) {
                    startRecording(editText.getText().toString());
                } else {
                    startRecording("ScreenRecorder-" + (new Date()).getTime());
                }
                dialog.dismiss();
            }
        });
        dialog.show();

        Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_noti)
                .setContentTitle("Screen Recorder")
                .setContentText("Screen recording...")
                .build();
        notification.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(SCREEN_RECORDER_NOTIFICATION, notification);
    }

    private final Handler drainHandler = new Handler(Looper.getMainLooper());
    private Runnable drainEncoderRunnable = new Runnable() {
        @Override
        public void run() {
            drainEncoder();
        }
    };

    private void startRecording(String fileName) {
        Display defaultDisplay = windowManager.getDefaultDisplay();

        if (defaultDisplay == null) {
            throw new RuntimeException("No display found.");
        }
        prepareVideoEncoder();

        try {
            muxer = new MediaMuxer(Environment.getExternalStorageDirectory() + "/Screen Recorder/" + fileName + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        // Get the display size and density.
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int screenDensity = metrics.densityDpi;

        // Start the video input.
        mediaProjection.createVirtualDisplay("Recording Display", screenWidth,
                screenHeight, screenDensity, 0 /* flags */, inputSurface,
                null /* callback */, null /* handler */);

        // Start the encoders
        drainEncoder();
    }

    private void prepareVideoEncoder() {
        videoBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, displaymetrics.widthPixels, displaymetrics.heightPixels);
        int frameRate = 30; // 30 fps

        // Set some required properties. The media codec may fail if these aren't defined.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000); // 6Mbps
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1 seconds between I-frames

        // Create a MediaCodec encoder and configure it. Get a Surface we can use for recording into.
        try {
            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoEncoder.createInputSurface();
            videoEncoder.start();
        } catch (IOException e) {
            releaseEncoders();
        }
    }

    private boolean drainEncoder() {
        drainHandler.removeCallbacks(drainEncoderRunnable);
        while (true) {
            int bufferIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, 0);

            if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // nothing available yet
                break;
            } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (trackIndex >= 0) {
                    throw new RuntimeException("format changed twice");
                }
                trackIndex = muxer.addTrack(videoEncoder.getOutputFormat());
                if (!muxerStarted && trackIndex >= 0) {
                    muxer.start();
                    muxerStarted = true;
                }
            } else if (bufferIndex < 0) {
                // not sure what's going on, ignore it
            } else {
                ByteBuffer encodedData = videoEncoder.getOutputBuffer(bufferIndex);
                if (encodedData == null) {
                    throw new RuntimeException("couldn't fetch buffer at index " + bufferIndex);
                }

                if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    videoBufferInfo.size = 0;
                }

                if (videoBufferInfo.size != 0) {
                    if (muxerStarted) {
                        encodedData.position(videoBufferInfo.offset);
                        encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size);
                        muxer.writeSampleData(trackIndex, encodedData, videoBufferInfo);
                    } else {
                        // muxer not started
                    }
                }

                videoEncoder.releaseOutputBuffer(bufferIndex, false);

                if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }

        drainHandler.postDelayed(drainEncoderRunnable, 10);
        return false;
    }

    private void releaseEncoders() {
        drainHandler.removeCallbacks(drainEncoderRunnable);
        if (muxer != null) {
            if (muxerStarted) {
                muxer.stop();
            }
            muxer.release();
            muxer = null;
            muxerStarted = false;
        }
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        videoBufferInfo = null;
        drainEncoderRunnable = null;
        trackIndex = -1;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseEncoders();
    }
}

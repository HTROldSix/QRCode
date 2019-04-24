package com.android.factory.tdcoder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.android.factory.R;
import com.android.factory.tdcoder.camera.CameraManager;
import com.android.factory.tdcoder.decoding.CaptureActivityHandler;
import com.android.factory.tdcoder.decoding.InactivityTimer;
import com.android.factory.tdcoder.view.ViewfinderView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

public class CaptureActivity extends Activity implements Callback {

    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    public InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep, testResult = true;
    private static final float BEEP_VOLUME = 0.10f;
    public static String TAG = "Tdcode";

    private static final String[] PERMISSION = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private Handler mHandler;
    private List<Boolean> mTestResult;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        requestPermissions(PERMISSION, 1);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(R.layout.tdcode);
        // CameraManager
        CameraManager.init(getApplication());
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
        mHandler = new Handler();
        mTestResult = new ArrayList<>();
    }

    public void closeDriver() {
        inactivityTimer.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException ioe) {
            Log.d(TAG, "IOException: " + ioe.toString());
            return;
        } catch (RuntimeException e) {
            Log.d(TAG, "RuntimeException: " + e.toString());
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;

    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();

    }

    public void handleDecode(final String result, Bitmap barcode) {
        inactivityTimer.onActivity();
        playBeepSoundAndVibrate();
        int state = CameraManager.get().getState();
        Log.i(TAG, "msg.toString() = " + CameraManager.get().getState());
        boolean isPass = getResources().getString(R.string.Camera_test1).equals(result)
                || getResources().getString(R.string.Camera_test2).equals(result);
        mTestResult.add(isPass);
        if (state != 4) {
            Toast.makeText(this, String.format(getString(R.string.toast), ++state,
                    isPass ? getResources().getString(R.string.pass) : getResources().getString(R.string.fail)
            ), Toast.LENGTH_SHORT).show();
            mHandler.postDelayed(restartPreviewRunnable, 1000);
            CameraManager.get().setState(state);
            Log.i(TAG, "msg.toString() = " + CameraManager.get().getState());
        } else {
            StringBuffer msg = new StringBuffer();
            for (int i = 0; i < mTestResult.size(); i++) {
                testResult &= mTestResult.get(i);
                msg.append(String.format(getString(R.string.item_result), i, mTestResult.get(i) ? getResources().getString(R.string.pass) :
                        getResources().getString(R.string.fail)));
            }
            msg.append("\n");
            msg.append(String.format(getString(R.string.result), testResult ? getResources().getString(R.string.pass) :
                    getResources().getString(R.string.fail)));
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            if (barcode == null) {
                dialog.setIcon(null);
            } else {
                Drawable drawable = new BitmapDrawable(barcode);
                dialog.setIcon(drawable);
            }
            dialog.setTitle(R.string.TD_results);
            dialog.setCancelable(false);
            Log.i(TAG, "msg.toString() = " + msg.toString());

            dialog.setMessage(msg.toString());
            dialog.setNegativeButton("退出", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CaptureActivity.this.finish();
                }
            });
            dialog.setPositiveButton(R.string.restart, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CameraManager.get().setState(0);
                    mTestResult.clear();
                    mHandler.removeCallbacks(mRunnable);
                    mHandler.post(restartPreviewRunnable);
                }
            });
            dialog.create().show();
            mHandler.postDelayed(mRunnable, 3000);
        }
    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (testResult) {
                initBeepSound();
            } else {
                initBeepSound();
            }
        }
    };

    private Runnable restartPreviewRunnable = new Runnable() {
        @Override
        public void run() {
            Message message = Message.obtain(getHandler(), R.id.restart_preview);
            message.sendToTarget();
        }
    };

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
    }

    public void showTimeOutDialog() {
        handleDecode(getResources().getString(R.string.td_time_out), null);
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };

}

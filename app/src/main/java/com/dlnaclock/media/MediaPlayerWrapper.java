package com.dlnaclock.media;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

public class MediaPlayerWrapper {

    private static final String TAG = "MediaPlayerWrapper";
    private static final int MAX_RETRY = 2;

    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private String currentDataSource;
    private boolean isPrepared = false;
    private boolean isVideoMode = false;

    private MediaPlayerListener listener;

    public interface MediaPlayerListener {
        void onPrepared();
        void onCompletion();
        void onError(int what, int extra);
        void onBufferingUpdate(int percent);
        void onVideoSizeChanged(int width, int height);
    }

    public void setListener(MediaPlayerListener listener) {
        this.listener = listener;
    }

    public void setDataSource(String dataSource) {
        this.currentDataSource = dataSource;
        this.retryCount = 0;
        preparePlayer();
    }

    private void preparePlayer() {
        release();

        try {
            mediaPlayer = new MediaPlayer();

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    isPrepared = true;
                    retryCount = 0;
                    if (listener != null) listener.onPrepared();
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (listener != null) listener.onCompletion();
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                    isPrepared = false;
                    if (retryCount < MAX_RETRY) {
                        retryCount++;
                        Log.i(TAG, "Retrying... attempt " + retryCount);
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                preparePlayer();
                            }
                        }, 1000);
                        return true;
                    }
                    if (listener != null) listener.onError(what, extra);
                    return true;
                }
            });

            mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    if (listener != null) listener.onBufferingUpdate(percent);
                }
            });

            mediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    isVideoMode = (width > 0 && height > 0);
                    if (listener != null) listener.onVideoSizeChanged(width, height);
                }
            });

            mediaPlayer.setDataSource(currentDataSource);
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "Error preparing MediaPlayer", e);
            if (retryCount < MAX_RETRY) {
                retryCount++;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        preparePlayer();
                    }
                }, 1000);
            } else {
                if (listener != null) listener.onError(-1, -1);
            }
        }
    }

    public void setSurface(Surface surface) {
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(surface);
        }
    }

    public void start() {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.start();
        }
    }

    public void pause() {
        if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(msec);
        }
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public boolean isPlaying() {
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.isPlaying();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public boolean isVideoMode() {
        return isVideoMode;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public void release() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {
                // ignore
            }
            mediaPlayer = null;
            isPrepared = false;
        }
    }
}

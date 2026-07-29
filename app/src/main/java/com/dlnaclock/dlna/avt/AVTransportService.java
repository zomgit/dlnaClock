package com.dlnaclock.dlna.avt;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

public class AVTransportService {

    private static final String TAG = "AVTransportService";

    private TransportState currentState = TransportState.NO_MEDIA_PRESENT;
    private MediaInfo mediaInfo = new MediaInfo();
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long currentPositionMs = 0;
    private long durationMs = 0;
    private boolean isPrepared = false;

    private CopyOnWriteArrayList<AVTransportListener> listeners = new CopyOnWriteArrayList<>();

    public interface AVTransportListener {
        void onUriSet(MediaInfo mediaInfo);
        void onStateChanged(TransportState state);
        void onPlaybackPositionChanged(long positionMs, long durationMs);
        void onMediaCompleted();
        void onError(int what, int extra);
    }

    public void addListener(AVTransportListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AVTransportListener listener) {
        listeners.remove(listener);
    }

    public void setAVTransportURI(int instanceId, String uri, String metadata) {
        Log.i(TAG, "setAVTransportURI: " + uri);

        // Stop current playback
        stopInternal();

        // Update media info
        mediaInfo.clear();
        mediaInfo.setCurrentUri(uri);
        if (metadata != null && !metadata.isEmpty()) {
            mediaInfo.parseMetadata(metadata);
        }

        // Determine MIME type from URI if not set
        if (mediaInfo.getMimeType() == null || mediaInfo.getMimeType().isEmpty()) {
            mediaInfo.setMimeType(guessMimeType(uri));
        }

        currentState = TransportState.STOPPED;

        // Notify listeners
        for (AVTransportListener l : listeners) {
            l.onUriSet(mediaInfo);
            l.onStateChanged(currentState);
        }
    }

    public void play(String speed) {
        Log.i(TAG, "play, current state: " + currentState);

        if (currentState == TransportState.NO_MEDIA_PRESENT) {
            Log.w(TAG, "No media to play");
            return;
        }

        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        isPrepared = true;
                        durationMs = mp.getDuration();
                        mediaInfo.setDuration(formatTime(durationMs));
                        mp.start();
                        currentState = TransportState.PLAYING;
                        notifyStateChanged();
                        startPositionUpdate();
                    }
                });
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        currentState = TransportState.STOPPED;
                        notifyStateChanged();
                        for (AVTransportListener l : listeners) {
                            l.onMediaCompleted();
                        }
                    }
                });
                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                        currentState = TransportState.STOPPED;
                        notifyStateChanged();
                        for (AVTransportListener l : listeners) {
                            l.onError(what, extra);
                        }
                        return true;
                    }
                });

                mediaPlayer.setDataSource(mediaInfo.getCurrentUri());
                currentState = TransportState.TRANSITIONING;
                notifyStateChanged();
                mediaPlayer.prepareAsync();
            } else {
                if (!isPrepared) {
                    mediaPlayer.prepareAsync();
                } else {
                    if (!mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                    }
                    currentState = TransportState.PLAYING;
                    notifyStateChanged();
                    startPositionUpdate();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting playback", e);
            currentState = TransportState.STOPPED;
            notifyStateChanged();
        }
    }

    public void pause() {
        Log.i(TAG, "pause, current state: " + currentState);
        if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            currentState = TransportState.PAUSED_PLAYBACK;
            notifyStateChanged();
            stopPositionUpdate();
        }
    }

    public void stop() {
        Log.i(TAG, "stop, current state: " + currentState);
        stopInternal();
        currentState = TransportState.STOPPED;
        notifyStateChanged();
    }

    public void seek(String unit, String target) {
        Log.i(TAG, "seek: " + unit + " -> " + target);
        if (mediaPlayer != null && isPrepared) {
            try {
                long targetMs = parseTimeToMs(target);
                mediaPlayer.seekTo((int) targetMs);
                currentPositionMs = targetMs;
            } catch (Exception e) {
                Log.e(TAG, "Error seeking", e);
            }
        }
    }

    public void next() {
        Log.i(TAG, "next - not implemented for single track");
    }

    public void previous() {
        Log.i(TAG, "previous - not implemented for single track");
    }

    public String[] getTransportInfo() {
        return new String[]{
                currentState.getValue(),
                currentState == TransportState.PLAYING || currentState == TransportState.PAUSED_PLAYBACK
                        ? "OK" : "OK",
                "1" // speed
        };
    }

    public String[] getMediaInfo() {
        String duration = mediaInfo.getDuration();
        String uri = mediaInfo.getCurrentUri();
        String metadata = ""; // Could include DIDL-Lite metadata
        return new String[]{"1", duration, uri, metadata};
    }

    public String[] getPositionInfo() {
        String duration = mediaInfo.getDuration();
        String uri = mediaInfo.getCurrentUri();
        String relTime = formatTime(currentPositionMs);
        return new String[]{duration, uri, relTime};
    }

    public TransportState getCurrentState() {
        return currentState;
    }

    public MediaInfo getMediaInfoObject() {
        return mediaInfo;
    }

    public long getCurrentPositionMs() {
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                return currentPositionMs;
            }
        }
        return currentPositionMs;
    }

    public long getDurationMs() {
        if (mediaPlayer != null && isPrepared) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                return durationMs;
            }
        }
        return durationMs;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    private void stopInternal() {
        stopPositionUpdate();
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaPlayer", e);
            }
            mediaPlayer = null;
            isPrepared = false;
        }
        currentPositionMs = 0;
    }

    private void notifyStateChanged() {
        for (AVTransportListener l : listeners) {
            l.onStateChanged(currentState);
        }
    }

    private Runnable positionUpdater = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPrepared && currentState == TransportState.PLAYING) {
                try {
                    currentPositionMs = mediaPlayer.getCurrentPosition();
                    durationMs = mediaPlayer.getDuration();
                    for (AVTransportListener l : listeners) {
                        l.onPlaybackPositionChanged(currentPositionMs, durationMs);
                    }
                } catch (Exception e) {
                    // ignore
                }
                handler.postDelayed(this, 1000);
            }
        }
    };

    private void startPositionUpdate() {
        handler.removeCallbacks(positionUpdater);
        handler.post(positionUpdater);
    }

    private void stopPositionUpdate() {
        handler.removeCallbacks(positionUpdater);
    }

    private String guessMimeType(String uri) {
        if (uri == null) return "application/octet-stream";
        String lower = uri.toLowerCase();
        if (lower.contains(".mp3")) return "audio/mpeg";
        if (lower.contains(".mp4") || lower.contains(".m4a")) return "video/mp4";
        if (lower.contains(".mkv")) return "video/x-matroska";
        if (lower.contains(".avi")) return "video/avi";
        if (lower.contains(".webm")) return "video/webm";
        if (lower.contains(".flac")) return "audio/flac";
        if (lower.contains(".wav")) return "audio/x-wav";
        if (lower.contains(".aac")) return "audio/aac";
        if (lower.contains(".ogg")) return "audio/ogg";
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".png")) return "image/png";
        if (lower.contains("audio")) return "audio/mpeg";
        if (lower.contains("video")) return "video/mp4";
        return "application/octet-stream";
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private long parseTimeToMs(String time) {
        if (time == null || time.isEmpty()) return 0;
        try {
            String[] parts = time.split(":");
            if (parts.length == 3) {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                long seconds = Long.parseLong(parts[2].split("\\.")[0]);
                return (hours * 3600 + minutes * 60 + seconds) * 1000;
            } else if (parts.length == 2) {
                long minutes = Long.parseLong(parts[0]);
                long seconds = Long.parseLong(parts[1].split("\\.")[0]);
                return (minutes * 60 + seconds) * 1000;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time: " + time, e);
        }
        return 0;
    }

    public void release() {
        stopInternal();
        listeners.clear();
    }
}

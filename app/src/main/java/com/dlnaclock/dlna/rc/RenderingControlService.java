package com.dlnaclock.dlna.rc;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

public class RenderingControlService {

    private static final String TAG = "RenderingControl";
    private Context context;
    private AudioManager audioManager;

    public RenderingControlService(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public int getVolume() {
        if (audioManager != null) {
            return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        return 50;
    }

    public void setVolume(int volume) {
        if (audioManager != null) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int targetVolume = Math.max(0, Math.min(volume * maxVolume / 100, maxVolume));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            Log.d(TAG, "Volume set to " + targetVolume + " (max: " + maxVolume + ")");
        }
    }

    public boolean getMute() {
        if (audioManager != null) {
            return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
        }
        return false;
    }

    public void setMute(boolean mute) {
        if (audioManager != null) {
            if (mute) {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            }
            Log.d(TAG, "Mute set to " + mute);
        }
    }
}

package com.example.protecther;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkStateReceiver extends BroadcastReceiver {
    public interface NetworkStateListener {
        void onNetworkStateChanged();
    }

    private NetworkStateListener listener;

    public NetworkStateReceiver(NetworkStateListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (listener != null) {
            listener.onNetworkStateChanged();
        }
    }
}

package com.example.downloaderdemo.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.example.downloaderdemo.EuroPMCApplication;
import com.example.downloaderdemo.event.BaseEvent;
import com.squareup.otto.Bus;

/**
 * Other fragments that need to either post to or subscribe to the event
 * buss can inherit from this class
 */
public class BaseFragment extends Fragment {

    public BaseFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getAppBus().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getAppBus().unregister(this);
    }

    protected Bus getAppBus() {
        return EuroPMCApplication.getInstance().getBus();
    }

    protected void postToAppBus(BaseEvent event) {
        EuroPMCApplication.postToBus(event);
    }

}

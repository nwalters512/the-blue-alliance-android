package com.thebluealliance.androidclient.eventbus;

public class TabSelectedAndSettledEvent {
    private int mIndex;

    public TabSelectedAndSettledEvent(int index) {
        mIndex = index;
    }

    public int getIndex() {
        return mIndex;
    }
}

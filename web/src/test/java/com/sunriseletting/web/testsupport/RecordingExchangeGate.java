package com.sunriseletting.web.testsupport;

import java.util.ArrayList;
import java.util.List;

import com.sunriseletting.web.security.RememberMeExchangeGate;

/** A gate for page tests, which have no Redis. Open unless a test shuts it. */
public class RecordingExchangeGate implements RememberMeExchangeGate {

    private boolean open = true;

    private final List<String> reopened = new ArrayList<>();

    public void shut() {
        open = false;
    }

    public void reset() {
        open = true;
        reopened.clear();
    }

    public List<String> reopened() {
        return reopened;
    }

    @Override
    public boolean tryEnter(String series) {
        return open;
    }

    @Override
    public void reopen(String series) {
        reopened.add(series);
    }
}

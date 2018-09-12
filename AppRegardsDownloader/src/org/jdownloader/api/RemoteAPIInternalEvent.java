package org.jdownloader.api;

import org.appwork.utils.event.DefaultEvent;

public abstract class RemoteAPIInternalEvent extends DefaultEvent {

    public RemoteAPIInternalEvent() {
        super(null);
    }

    abstract public void fireTo(RemoteAPIInternalEventListener listener);
}
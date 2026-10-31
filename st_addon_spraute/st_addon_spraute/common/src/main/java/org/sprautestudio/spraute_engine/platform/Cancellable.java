package org.sprautestudio.spraute_engine.platform;

/** An event a listener can stop: what it stands for does not happen. */
public abstract class Cancellable {
    private boolean canceled;

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}

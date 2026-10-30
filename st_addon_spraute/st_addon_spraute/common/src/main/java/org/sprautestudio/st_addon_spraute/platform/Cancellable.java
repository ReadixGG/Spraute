package org.sprautestudio.st_addon_spraute.platform;

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

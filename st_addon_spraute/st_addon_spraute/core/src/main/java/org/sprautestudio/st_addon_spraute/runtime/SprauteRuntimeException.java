package org.sprautestudio.st_addon_spraute.runtime;

/** Error raised while a Spraute script runs; reported to the editor console. */
public class SprauteRuntimeException extends RuntimeException {
    public SprauteRuntimeException(String message) {
        super(message);
    }
}

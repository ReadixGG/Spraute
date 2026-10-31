package org.sprautestudio.spraute_engine.runtime;

/** Error raised while a Spraute script runs; reported to the editor console. */
public class SprauteRuntimeException extends RuntimeException {
    public SprauteRuntimeException(String message) {
        super(message);
    }
}

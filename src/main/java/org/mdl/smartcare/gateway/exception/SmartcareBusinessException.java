package org.mdl.smartcare.gateway.exception;

public class SmartcareBusinessException extends RuntimeException{

    public SmartcareBusinessException(String message) {
        super(message);
    }

    public SmartcareBusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public SmartcareBusinessException(Throwable cause) {
        super(cause);
    }

    public SmartcareBusinessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}

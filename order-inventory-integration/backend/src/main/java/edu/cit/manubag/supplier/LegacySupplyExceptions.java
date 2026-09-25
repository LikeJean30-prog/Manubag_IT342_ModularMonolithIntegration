package edu.cit.manubag.supplier;

final class LegacySupplyExceptions {

    private LegacySupplyExceptions() {
    }

    static class SessionInvalidException extends RuntimeException {
        SessionInvalidException(String message) {
            super(message);
        }
    }

    static class AuthRejectedException extends RuntimeException {
        AuthRejectedException(String message) {
            super(message);
        }
    }

    static class NonRetryableSupplierException extends RuntimeException {
        final String code;

        NonRetryableSupplierException(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    static class SupplierUnavailableException extends RuntimeException {
        SupplierUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        SupplierUnavailableException(String message) {
            super(message);
        }
    }

    static class RequestIdConflictException extends RuntimeException {
        RequestIdConflictException(String message) {
            super(message);
        }
    }
}

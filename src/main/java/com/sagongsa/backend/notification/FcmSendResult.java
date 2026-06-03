package com.sagongsa.backend.notification;

public record FcmSendResult(
	boolean sent,
	boolean invalidToken
) {

	public static FcmSendResult success() {
		return new FcmSendResult(true, false);
	}

	public static FcmSendResult invalid() {
		return new FcmSendResult(false, true);
	}

	public static FcmSendResult failed() {
		return new FcmSendResult(false, false);
	}
}

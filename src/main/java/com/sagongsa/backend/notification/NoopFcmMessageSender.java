package com.sagongsa.backend.notification;

public class NoopFcmMessageSender implements FcmMessageSender {

	@Override
	public FcmSendResult send(FcmSendRequest request) {
		return FcmSendResult.failed();
	}
}

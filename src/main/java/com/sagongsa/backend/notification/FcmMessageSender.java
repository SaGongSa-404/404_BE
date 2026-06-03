package com.sagongsa.backend.notification;

public interface FcmMessageSender {

	FcmSendResult send(FcmSendRequest request);
}

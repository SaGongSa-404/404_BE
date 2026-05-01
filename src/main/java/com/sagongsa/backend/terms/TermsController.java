package com.sagongsa.backend.terms;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/terms")
public class TermsController {

	private static final List<TermsSummaryResponse> TERMS_LIST = List.of(
		new TermsSummaryResponse(TermsType.SERVICE, "서비스 이용약관", true),
		new TermsSummaryResponse(TermsType.PRIVACY, "개인정보처리방침", true),
		new TermsSummaryResponse(TermsType.MARKETING, "마케팅 수신 동의", false)
	);

	private static final Map<TermsType, TermsDetailResponse> TERMS_DETAIL = Map.of(
		TermsType.SERVICE, new TermsDetailResponse(TermsType.SERVICE, "서비스 이용약관", true,
			"""
			제1조 (목적)
			본 약관은 위굴(Wigul) 서비스의 이용 조건 및 절차, 회사와 이용자 간의 권리·의무 및 책임 사항을 규정함을 목적으로 합니다.

			제2조 (용어의 정의)
			1. "서비스"란 회사가 제공하는 충동구매 방지 앱 위굴의 모든 기능을 의미합니다.
			2. "이용자"란 본 약관에 동의하고 서비스를 이용하는 회원을 의미합니다.

			제3조 (서비스 이용)
			이용자는 소셜 로그인을 통해 회원가입 후 서비스를 이용할 수 있습니다.

			제4조 (계정 탈퇴)
			이용자는 언제든지 계정 탈퇴를 요청할 수 있으며, 탈퇴 시 모든 데이터는 즉시 삭제됩니다.
			"""),
		TermsType.PRIVACY, new TermsDetailResponse(TermsType.PRIVACY, "개인정보처리방침", true,
			"""
			1. 수집하는 개인정보 항목
			- 소셜 로그인 시: 이름, 이메일, 프로필 이미지

			2. 개인정보 수집 목적
			- 서비스 제공 및 회원 관리

			3. 개인정보 보유 기간
			- 회원 탈퇴 시 즉시 삭제

			4. 개인정보 제3자 제공
			- 법령에 의한 경우를 제외하고 제3자에게 제공하지 않습니다.
			"""),
		TermsType.MARKETING, new TermsDetailResponse(TermsType.MARKETING, "마케팅 수신 동의", false,
			"""
			위굴 서비스의 새로운 기능 안내, 이벤트 및 혜택 정보를 푸시 알림으로 수신하는 것에 동의합니다.
			본 동의는 선택 사항이며, 동의하지 않아도 서비스 이용에 제한이 없습니다.
			동의 후에도 알림 설정에서 언제든지 변경 가능합니다.
			""")
	);

	@GetMapping
	public ResponseEntity<List<TermsSummaryResponse>> list() {
		return ResponseEntity.ok(TERMS_LIST);
	}

	@GetMapping("/{type}")
	public ResponseEntity<TermsDetailResponse> detail(@PathVariable TermsType type) {
		TermsDetailResponse detail = TERMS_DETAIL.get(type);
		if (detail == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "약관을 찾을 수 없습니다.");
		}
		return ResponseEntity.ok(detail);
	}
}

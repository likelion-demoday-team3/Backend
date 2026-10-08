package com.example.kiwi_backend.wallet.api;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.common.security.AuthUser;
import com.example.kiwi_backend.wallet.api.dto.WalletBalanceResponse;
import com.example.kiwi_backend.wallet.api.dto.WalletHistoryResponse;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 내 보석 조회. 보석을 바꾸는 API는 여기 없다 (변동은 세션 신청, 결제 등 각 기능을 통해서만) */
@RestController
@RequestMapping("/wallet/my")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ApiResponse<WalletBalanceResponse> getBalance(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(walletService.getBalance(authUser.memberId()));
    }

    @GetMapping("/history")
    public ApiResponse<List<WalletHistoryResponse>> getHistory(@AuthenticationPrincipal AuthUser authUser,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(walletService.getHistory(authUser.memberId(), page, size));
    }
}

package com.example.kiwi_backend.wallet.application.service;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.command.GemWallet;
import com.example.kiwi_backend.contract.dto.GemCommand;
import com.example.kiwi_backend.contract.dto.GemResult;
import com.example.kiwi_backend.wallet.api.dto.WalletBalanceResponse;
import com.example.kiwi_backend.wallet.api.dto.WalletHistoryResponse;
import com.example.kiwi_backend.wallet.domain.entity.Wallet;
import com.example.kiwi_backend.wallet.domain.entity.WalletHistory;
import com.example.kiwi_backend.wallet.domain.repository.WalletHistoryRepository;
import com.example.kiwi_backend.wallet.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 보석 잔액과 내역의 유일한 관리자.
 *
 * 동시성 처리 방식
 * 1. 지갑 행을 먼저 잠근다 (비관적 락). 같은 회원의 보석 변동은 한 줄로 서서 하나씩 처리된다.
 * 2. 잠금을 얻은 "뒤에" 멱등 키를 확인한다.
 *    같은 요청이 동시에 두 번 오면, 두 번째는 첫 번째가 끝날 때까지 기다렸다가 키가 이미 있는 걸 보고 그대로 돌아간다.
 *    순서를 반대로(키 확인 → 잠금) 하면 둘 다 "아직 없음"을 보고 두 번 반영될 수 있다.
 * 3. 잔액 변경과 내역 기록은 한 트랜잭션. 둘 중 하나만 남는 일이 없다.
 */
@Service
@RequiredArgsConstructor
public class WalletService implements GemWallet {

    private static final int MAX_HISTORY_PAGE_SIZE = 50;

    private final WalletRepository walletRepository;
    private final WalletHistoryRepository historyRepository;

    // ===== 다른 모듈이 부르는 보석 변동 =====

    @Override
    @Transactional
    public GemResult apply(GemCommand command) {
        Wallet wallet = walletRepository.findByMemberIdForUpdate(command.memberId())
                .orElseThrow(() -> new NotFoundException("지갑이 없습니다"));

        Optional<WalletHistory> existing = historyRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            WalletHistory history = existing.get();
            if (!history.matches(command.memberId(), command.type(), command.amount())) {
                throw new IllegalStateException("같은 멱등 키로 다른 내용의 보석 요청이 들어왔습니다");
            }
            return new GemResult(history.getId(), wallet.getGemBalance(), true);
        }

        wallet.apply(command.type(), command.amount());   // 잔액 부족이면 여기서 InsufficientGemException

        WalletHistory history = historyRepository.save(WalletHistory.record(
                command.memberId(),
                command.type(),
                command.amount(),
                wallet.getGemBalance(),
                command.sessionId(),
                command.paymentId(),
                command.memo(),
                command.idempotencyKey()
        ));

        return new GemResult(history.getId(), wallet.getGemBalance(), false);
    }

    // ===== 지갑 생성 (가입 시) =====

    /** 이미 있으면 아무것도 하지 않는다. 가입 이벤트가 두 번 와도 지갑은 하나 */
    @Transactional
    public void createWallet(Long memberId) {
        if (walletRepository.existsByMemberId(memberId)) {
            return;
        }
        walletRepository.save(Wallet.create(memberId));
    }

    // ===== 조회 =====

    @Transactional(readOnly = true)
    public WalletBalanceResponse getBalance(Long memberId) {
        Wallet wallet = walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NotFoundException("지갑이 없습니다"));
        return new WalletBalanceResponse(wallet.getGemBalance());
    }

    @Transactional(readOnly = true)
    public List<WalletHistoryResponse> getHistory(Long memberId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_PAGE_SIZE);
        return historyRepository
                .findByMemberIdOrderByCreatedAtDescIdDesc(memberId, PageRequest.of(safePage, safeSize))
                .map(WalletHistoryResponse::from)
                .getContent();
    }
}

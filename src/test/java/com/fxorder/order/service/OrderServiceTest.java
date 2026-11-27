package com.fxorder.order.service;

import com.fxorder.balance.entity.Balance;
import com.fxorder.balance.repository.BalanceRepository;
import com.fxorder.enums.CurrencyCode;
import com.fxorder.exchangerate.entity.ExchangeRateHistory;
import com.fxorder.exchangerate.repository.ExchangeRateHistoryRepository;
import com.fxorder.order.dto.OrderRequest;
import com.fxorder.order.dto.OrderResponse;
import com.fxorder.order.entity.OrderHistory;
import com.fxorder.order.repository.OrderHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private ExchangeRateHistoryRepository exchangeRateHistoryRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private OrderService orderService;

    private void givenRate(CurrencyCode currency, String baseRate, String buyRate, String sellRate) {
        given(exchangeRateHistoryRepository.findFirstByCurrencyCodeOrderByDateTimeDesc(currency))
                .willReturn(Optional.of(ExchangeRateHistory.builder()
                        .currencyCode(currency)
                        .baseRate(new BigDecimal(baseRate))
                        .buyRate(new BigDecimal(buyRate))
                        .sellRate(new BigDecimal(sellRate))
                        .build()));
    }

    private Balance givenBalance(CurrencyCode currency, String amount) {
        Balance balance = Balance.builder()
                .currencyCode(currency)
                .amount(new BigDecimal(amount))
                .build();
        given(balanceRepository.findWithLockByCurrencyCode(currency)).willReturn(Optional.of(balance));
        return balance;
    }

    private void givenOrderSaveReturnsArgument() {
        given(orderHistoryRepository.save(any(OrderHistory.class))).willAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("JPY 매수: 환율이 100엔당이므로 1000 JPY는 9975원이 청구된다")
    void buyJpyDividesByUnit() {
        givenRate(CurrencyCode.JPY, "950.00", "997.50", "902.50");
        Balance krw = givenBalance(CurrencyCode.KRW, "1000000");
        Balance jpy = givenBalance(CurrencyCode.JPY, "0");
        givenOrderSaveReturnsArgument();

        OrderResponse response = orderService.createOrder(
                new OrderRequest(new BigDecimal("1000"), CurrencyCode.KRW, CurrencyCode.JPY));

        assertThat(response.getFromAmount()).isEqualByComparingTo("9975");
        assertThat(response.getToAmount()).isEqualByComparingTo("1000");
        assertThat(krw.getAmount()).isEqualByComparingTo("990025");
        assertThat(jpy.getAmount()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("JPY 매도: 1000 JPY를 팔면 9025원을 받는다")
    void sellJpyDividesByUnit() {
        givenRate(CurrencyCode.JPY, "950.00", "997.50", "902.50");
        Balance jpy = givenBalance(CurrencyCode.JPY, "5000");
        Balance krw = givenBalance(CurrencyCode.KRW, "0");
        givenOrderSaveReturnsArgument();

        OrderResponse response = orderService.createOrder(
                new OrderRequest(new BigDecimal("1000"), CurrencyCode.JPY, CurrencyCode.KRW));

        assertThat(response.getFromAmount()).isEqualByComparingTo("1000");
        assertThat(response.getToAmount()).isEqualByComparingTo("9025");
        assertThat(jpy.getAmount()).isEqualByComparingTo("4000");
        assertThat(krw.getAmount()).isEqualByComparingTo("9025");
    }

    @Test
    @DisplayName("KRW -> KRW 같은 통화 주문은 거부된다")
    void rejectsSameCurrencyKrwOrder() {
        assertThatThrownBy(() -> orderService.createOrder(
                new OrderRequest(new BigDecimal("1000"), CurrencyCode.KRW, CurrencyCode.KRW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같은 통화");

        then(exchangeRateHistoryRepository).shouldHaveNoInteractions();
        then(balanceRepository).shouldHaveNoInteractions();
        then(orderHistoryRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("USD -> USD 같은 통화 주문도 거부된다")
    void rejectsSameCurrencyForeignOrder() {
        assertThatThrownBy(() -> orderService.createOrder(
                new OrderRequest(new BigDecimal("100"), CurrencyCode.USD, CurrencyCode.USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같은 통화");
    }

    @Test
    @DisplayName("USD 매수: 단위가 1인 통화는 환산이 그대로다")
    void buyUsdKeepsRateAsIs() {
        givenRate(CurrencyCode.USD, "1300.00", "1365.00", "1235.00");
        Balance krw = givenBalance(CurrencyCode.KRW, "1000000");
        Balance usd = givenBalance(CurrencyCode.USD, "0");
        givenOrderSaveReturnsArgument();

        OrderResponse response = orderService.createOrder(
                new OrderRequest(new BigDecimal("100"), CurrencyCode.KRW, CurrencyCode.USD));

        assertThat(response.getFromAmount()).isEqualByComparingTo("136500");
        assertThat(response.getToAmount()).isEqualByComparingTo("100");
        assertThat(krw.getAmount()).isEqualByComparingTo("863500");
        assertThat(usd.getAmount()).isEqualByComparingTo("100");
    }
}

package com.fxorder.exchangerate.service;

import com.fxorder.enums.CurrencyCode;
import com.fxorder.exchangerate.dto.ExchangeRateDto;
import com.fxorder.exchangerate.dto.ExchangeRateLatestResponse;
import com.fxorder.exchangerate.entity.ExchangeRateHistory;
import com.fxorder.exchangerate.repository.ExchangeRateHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateHistoryRepository exchangeRateHistoryRepository;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    private ExchangeRateHistory rate(CurrencyCode currency, String baseRate) {
        return ExchangeRateHistory.builder()
                .currencyCode(currency)
                .baseRate(new BigDecimal(baseRate))
                .buyRate(new BigDecimal(baseRate))
                .sellRate(new BigDecimal(baseRate))
                .build();
    }

    @Test
    @DisplayName("환율 목록에 기준 통화 KRW 행은 넣지 않는다")
    void latestRatesExcludeKrw() {
        given(exchangeRateHistoryRepository.findLatest())
                .willReturn(List.of(rate(CurrencyCode.USD, "1400.00"), rate(CurrencyCode.KRW, "1.00")));

        ExchangeRateLatestResponse response = exchangeRateService.getLatestRates();

        assertThat(response.getExchangeRateList())
                .extracting(ExchangeRateDto::getCurrency)
                .containsExactly("USD");
    }
}

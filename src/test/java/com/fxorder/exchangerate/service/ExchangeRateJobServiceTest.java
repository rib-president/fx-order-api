package com.fxorder.exchangerate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxorder.enums.CurrencyCode;
import com.fxorder.exchangerate.dto.ExchangeRateApiResponse;
import com.fxorder.exchangerate.entity.ExchangeRateHistory;
import com.fxorder.exchangerate.repository.ExchangeRateHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExchangeRateJobServiceTest {

    private static final String API_URL = "http://exchange-rate.test/latest";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ExchangeRateHistoryRepository exchangeRateHistoryRepository;

    @InjectMocks
    private ExchangeRateJobService exchangeRateJobService;

    @Captor
    private ArgumentCaptor<List<ExchangeRateHistory>> historiesCaptor;

    @BeforeEach
    void setApiUrl() {
        ReflectionTestUtils.setField(exchangeRateJobService, "apiUrl", API_URL);
    }

    private void givenApiRates(String json) throws Exception {
        ExchangeRateApiResponse response = new ObjectMapper().readValue(json, ExchangeRateApiResponse.class);
        given(restTemplate.getForObject(API_URL, ExchangeRateApiResponse.class)).willReturn(response);
    }

    @Test
    @DisplayName("기준 통화 KRW의 환율 행은 저장하지 않는다")
    void doesNotSaveKrwRate() throws Exception {
        givenApiRates("{\"result\":\"success\",\"base_code\":\"USD\","
                + "\"rates\":{\"USD\":1,\"KRW\":1400,\"JPY\":150,\"CNY\":7,\"EUR\":0.9}}");

        exchangeRateJobService.collectExchangeRates();

        verify(exchangeRateHistoryRepository).saveAll(historiesCaptor.capture());
        assertThat(historiesCaptor.getValue())
                .extracting(ExchangeRateHistory::getCurrencyCode)
                .doesNotContain(CurrencyCode.KRW);
    }

    @Test
    @DisplayName("KRW 외의 통화는 그대로 저장한다")
    void savesForeignRates() throws Exception {
        givenApiRates("{\"result\":\"success\",\"base_code\":\"USD\","
                + "\"rates\":{\"USD\":1,\"KRW\":1400,\"JPY\":150,\"CNY\":7,\"EUR\":0.9}}");

        exchangeRateJobService.collectExchangeRates();

        verify(exchangeRateHistoryRepository).saveAll(historiesCaptor.capture());
        assertThat(historiesCaptor.getValue())
                .extracting(ExchangeRateHistory::getCurrencyCode)
                .contains(CurrencyCode.USD, CurrencyCode.JPY, CurrencyCode.CNY, CurrencyCode.EUR);
        assertThat(historiesCaptor.getValue())
                .filteredOn(history -> history.getCurrencyCode() == CurrencyCode.USD)
                .extracting(ExchangeRateHistory::getBaseRate)
                .containsExactly(new BigDecimal("1400.00"));
    }
}

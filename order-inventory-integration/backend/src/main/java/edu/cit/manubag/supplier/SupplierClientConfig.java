package edu.cit.manubag.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SupplierProperties.class)
class SupplierClientConfig {

    @Bean
    XmlMapper legacySupplyXmlMapper() {
        return new XmlMapper();
    }

    @Bean
    RestClient supplierRestClient(SupplierProperties properties, XmlMapper legacySupplyXmlMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(properties.getTimeoutMs());
        requestFactory.setReadTimeout(properties.getTimeoutMs());

        MappingJackson2XmlHttpMessageConverter xmlConverter =
                new MappingJackson2XmlHttpMessageConverter(legacySupplyXmlMapper);

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(xmlConverter);
                })
                .build();
    }
}

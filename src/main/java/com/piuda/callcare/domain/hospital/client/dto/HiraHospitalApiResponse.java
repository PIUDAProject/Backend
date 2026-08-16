package com.piuda.callcare.domain.hospital.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "response")
@JsonIgnoreProperties(ignoreUnknown = true)
public record HiraHospitalApiResponse(
        @JacksonXmlProperty(localName = "header") Header header,
        @JacksonXmlProperty(localName = "body") Body body
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
            @JacksonXmlProperty(localName = "resultCode") String resultCode,
            @JacksonXmlProperty(localName = "resultMsg") String resultMsg
    ) {
        public boolean isSuccess() {
            return "00".equals(resultCode);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            @JacksonXmlProperty(localName = "items") Items items,
            @JacksonXmlProperty(localName = "numOfRows") int numOfRows,
            @JacksonXmlProperty(localName = "pageNo") int pageNo,
            @JacksonXmlProperty(localName = "totalCount") int totalCount
    ) {
        public List<Item> itemList() {
            return items == null || items.item() == null ? List.of() : items.item();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "item")
            List<Item> item
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JacksonXmlProperty(localName = "ykiho") String ykiho,     // 암호화된 요양기호 -> externalId
            @JacksonXmlProperty(localName = "yadmNm") String yadmNm,   // 병원명
            @JacksonXmlProperty(localName = "addr") String addr,       // 주소
            @JacksonXmlProperty(localName = "telno") String telno      // 전화번호
    ) {
    }
}

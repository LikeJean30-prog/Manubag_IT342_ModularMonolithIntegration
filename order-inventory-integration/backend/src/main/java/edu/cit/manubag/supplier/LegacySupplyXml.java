package edu.cit.manubag.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

final class LegacySupplyXml {

    private LegacySupplyXml() {
    }

    @JacksonXmlRootElement(localName = "AuthRequest")
    static class AuthRequest {
        @JacksonXmlProperty(localName = "ClientId")
        String clientId;
        @JacksonXmlProperty(localName = "ApiKey")
        String apiKey;

        AuthRequest() {
        }

        AuthRequest(String clientId, String apiKey) {
            this.clientId = clientId;
            this.apiKey = apiKey;
        }
    }

    @JacksonXmlRootElement(localName = "AuthResponse")
    static class AuthResponse {
        @JacksonXmlProperty(localName = "SessionToken")
        String sessionToken;
        @JacksonXmlProperty(localName = "IssuedAt")
        String issuedAt;
    }

    @JacksonXmlRootElement(localName = "PurchaseOrder")
    static class PurchaseOrderRequest {
        @JacksonXmlProperty(localName = "SupplierSku")
        String supplierSku;
        @JacksonXmlProperty(localName = "Qty")
        int qty;
        @JacksonXmlProperty(localName = "BuyerRef")
        String buyerRef;

        PurchaseOrderRequest() {
        }

        PurchaseOrderRequest(String supplierSku, int qty, String buyerRef) {
            this.supplierSku = supplierSku;
            this.qty = qty;
            this.buyerRef = buyerRef;
        }
    }

    @JacksonXmlRootElement(localName = "PurchaseOrderAck")
    static class PurchaseOrderAck {
        @JacksonXmlProperty(localName = "PoNumber")
        String poNumber;
        @JacksonXmlProperty(localName = "StatusCode")
        int statusCode;
        @JacksonXmlProperty(localName = "SupplierSku")
        String supplierSku;
        @JacksonXmlProperty(localName = "Qty")
        int qty;
        @JacksonXmlProperty(localName = "Uom")
        String uom;
        @JacksonXmlProperty(localName = "BuyerRef")
        String buyerRef;
        @JacksonXmlProperty(localName = "CreatedAt")
        String createdAt;
    }

    @JacksonXmlRootElement(localName = "PurchaseOrderStatus")
    static class PurchaseOrderStatus {
        @JacksonXmlProperty(localName = "PoNumber")
        String poNumber;
        @JacksonXmlProperty(localName = "StatusCode")
        int statusCode;
        @JacksonXmlProperty(localName = "SupplierSku")
        String supplierSku;
        @JacksonXmlProperty(localName = "Qty")
        int qty;
        @JacksonXmlProperty(localName = "Uom")
        String uom;
        @JacksonXmlProperty(localName = "BuyerRef")
        String buyerRef;
        @JacksonXmlProperty(localName = "CreatedAt")
        String createdAt;
        @JacksonXmlProperty(localName = "CheckedAt")
        String checkedAt;
    }

    @JacksonXmlRootElement(localName = "PurchaseOrderList")
    static class PurchaseOrderList {
        @JacksonXmlProperty(localName = "Count")
        int count;
    }

    @JacksonXmlRootElement(localName = "LSError")
    static class LSError {
        @JacksonXmlProperty(localName = "Code")
        String code;
        @JacksonXmlProperty(localName = "Message")
        String message;
    }
}

package org.dspace.app.rest.vnpay;

public class VnpayCreatePaymentRequest {

    private String bitstreamId;
    private Long amount;
    private String orderInfo;
    private String returnUrl;
    private String email;
    private String name;

    public String getBitstreamId() {
        return bitstreamId;
    }

    public void setBitstreamId(String bitstreamId) {
        this.bitstreamId = bitstreamId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getOrderInfo() {
        return orderInfo;
    }

    public void setOrderInfo(String orderInfo) {
        this.orderInfo = orderInfo;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
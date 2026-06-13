/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.vnpay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.dspace.app.rest.vnpay.dao.VnpayTransactionDAO;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VnpayPaymentService {

    private static final int PAYMENT_EXPIRATION_MINUTES = 15;
    private static final TimeZone VNPAY_TIME_ZONE = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

    private final String vnpUrl;
    private final String vnpTmnCode;
    private final String vnpHashSecret;
    private final VnpayTransactionDAO vnpayTransactionDAO;

    public VnpayPaymentService(
        VnpayTransactionDAO vnpayTransactionDAO,
        @Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String vnpUrl,
        @Value("${vnpay.tmn-code:A1YS13CE}") String vnpTmnCode,
        @Value("${vnpay.hash-secret:F1YSSATU9UCJ709ZFBEH9E4TKSLNVCRM}") String vnpHashSecret
    ) {
        this.vnpayTransactionDAO = vnpayTransactionDAO;
        this.vnpUrl = vnpUrl;
        this.vnpTmnCode = vnpTmnCode;
        this.vnpHashSecret = vnpHashSecret;
    }

    public VnpayTransaction createTransaction(Context context, VnpayCreatePaymentRequest request, String callbackUrl)
        throws SQLException {
        VnpayTransaction transaction = new VnpayTransaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setBitstreamId(UUID.fromString(request.getBitstreamId()));
        transaction.setAmount(new BigDecimal(request.getAmount()));
        transaction.setOrderInfo(request.getOrderInfo());
        transaction.setEmail(request.getEmail());
        transaction.setName(request.getName());
        transaction.setClientReturnUrl(request.getReturnUrl());
        transaction.setCallbackUrl(callbackUrl);
        transaction.setStatus(VnpayTransactionStatus.PENDING);
        transaction.setCreatedAt(new Date());

        return vnpayTransactionDAO.create(context, transaction);
    }

    public String buildPaymentUrl(VnpayTransaction transaction, String clientIpAddress) {
        Calendar calendar = Calendar.getInstance(VNPAY_TIME_ZONE);
        Date createDate = calendar.getTime();
        calendar.add(Calendar.MINUTE, PAYMENT_EXPIRATION_MINUTES);
        Date expireDate = calendar.getTime();

        SortedMap<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", vnpTmnCode);
        fields.put("vnp_Amount", transaction.getAmount()
            .multiply(new BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString());
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", transaction.getTransactionId());
        fields.put("vnp_OrderInfo", transaction.getOrderInfo());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", transaction.getCallbackUrl());
        fields.put("vnp_CreateDate", formatDate(createDate));
        fields.put("vnp_ExpireDate", formatDate(expireDate));
        fields.put("vnp_IpAddr", clientIpAddress);

        String secureHash = hashAllFields(fields, vnpHashSecret);
        fields.put("vnp_SecureHash", secureHash);

        return buildQueryString(vnpUrl, fields);
    }

    public VnpayTransaction getTransaction(Context context, String transactionId) throws SQLException {
        return vnpayTransactionDAO.findByTransactionId(context, transactionId);
    }

    public boolean verifyVnpaySecureHash(Map<String, String> queryParams) {
        String secureHash = queryParams.get("vnp_SecureHash");
        if (secureHash == null || secureHash.isEmpty()) {
            return false;
        }

        SortedMap<String, String> fields = new TreeMap<>();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            if ("vnp_SecureHash".equals(key) || "vnp_SecureHashType".equals(key)) {
                continue;
            }
            fields.put(key, entry.getValue());
        }

        String expectedHash = hashAllFields(fields, vnpHashSecret);
        return secureHash.equalsIgnoreCase(expectedHash);
    }

    public VnpayTransaction updateTransactionStatus(Context context, Map<String, String> queryParams)
        throws SQLException {
        String txnRef = queryParams.get("vnp_TxnRef");
        if (txnRef == null) {
            return null;
        }

        VnpayTransaction transaction = getTransaction(context, txnRef);
        if (transaction == null) {
            return null;
        }

        if (!verifyVnpaySecureHash(queryParams)) {
            transaction.setStatus(VnpayTransactionStatus.FAILED);
            transaction.setResponseCode(queryParams.get("vnp_ResponseCode"));
            vnpayTransactionDAO.save(context, transaction);
            return transaction;
        }

        String responseCode = queryParams.get("vnp_ResponseCode");
        transaction.setResponseCode(responseCode);
        transaction.setBankTranNo(queryParams.get("vnp_BankTranNo"));
        transaction.setCardType(queryParams.get("vnp_CardType"));
        transaction.setPayDate(queryParams.get("vnp_PayDate"));

        if ("00".equals(responseCode)) {
            transaction.setStatus(VnpayTransactionStatus.SUCCESS);
        } else {
            transaction.setStatus(VnpayTransactionStatus.FAILED);
        }

        vnpayTransactionDAO.save(context, transaction);
        return transaction;
    }

    private String buildQueryString(String baseUrl, Map<String, String> fields) {
        StringBuilder builder = new StringBuilder(baseUrl);
        if (!baseUrl.contains("?")) {
            builder.append('?');
        } else if (!baseUrl.endsWith("?") && !baseUrl.endsWith("&")) {
            builder.append('&');
        }

        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            pairs.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
        }
        builder.append(String.join("&", pairs));
        return builder.toString();
    }

    private String hashAllFields(SortedMap<String, String> fields, String secret) {
        StringBuilder hashData = new StringBuilder();

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }

            if (hashData.length() > 0) {
                hashData.append('&');
            }

            hashData.append(entry.getKey())
                .append('=')
                .append(urlEncode(entry.getValue()));
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA512"
            );
            hmac.init(keySpec);

            byte[] digest = hmac.doFinal(hashData.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to create secure hash", e);
        }
    }

    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(VNPAY_TIME_ZONE);
        return formatter.format(date);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

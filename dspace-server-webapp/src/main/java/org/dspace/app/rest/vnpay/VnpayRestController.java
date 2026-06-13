/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.vnpay;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vnpay")
public class VnpayRestController {

    @Autowired
    private VnpayPaymentService vnpayPaymentService;

    @RequestMapping(value = "/create-payment", method = RequestMethod.POST)
    public ResponseEntity<VnpayCreatePaymentResponse> createPayment(
        HttpServletRequest request,
        @RequestBody VnpayCreatePaymentRequest createRequest
    ) throws Exception {
        if (createRequest == null || createRequest.getAmount() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Context context = ContextUtil.obtainContext(request);
        try {
            String callbackUrl = buildCallbackUrl(request);
            VnpayTransaction transaction = vnpayPaymentService.createTransaction(context, createRequest, callbackUrl);
            String paymentUrl = vnpayPaymentService.buildPaymentUrl(transaction, request.getRemoteAddr());

            VnpayCreatePaymentResponse response = new VnpayCreatePaymentResponse();
            response.setPaymentUrl(paymentUrl);
            response.setTransactionId(transaction.getTransactionId());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            ContextUtil.abortContext(request);
            throw ex;
        } finally {
            ContextUtil.completeContext(request);
        }
    }

    @RequestMapping(value = "/status/{transactionId}", method = RequestMethod.GET)
    public ResponseEntity<VnpayStatusResponse> getStatus(HttpServletRequest request, @PathVariable String transactionId) throws Exception {
        Context context = ContextUtil.obtainContext(request);
        try {
            VnpayTransaction transaction = vnpayPaymentService.getTransaction(context, transactionId);
            if (transaction == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            VnpayStatusResponse response = new VnpayStatusResponse();
            response.setTransactionId(transaction.getTransactionId());
            response.setSuccess(transaction.getStatus() == VnpayTransactionStatus.SUCCESS);
            response.setStatus(transaction.getStatus().name());
            response.setResponseCode(transaction.getResponseCode());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            ContextUtil.abortContext(request);
            throw ex;
        } finally {
            ContextUtil.completeContext(request);
        }
    }

    @RequestMapping(value = "/return", method = RequestMethod.GET)
    public void vnpayReturn(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Context context = ContextUtil.obtainContext(request);
        try {
            Map<String, String> queryParams = extractQueryParams(request);
            VnpayTransaction transaction = vnpayPaymentService.updateTransactionStatus(context, queryParams);

            if (transaction == null) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.getWriter().write("Invalid transaction or missing vnp_TxnRef");
                return;
            }

            boolean success = transaction.getStatus() == VnpayTransactionStatus.SUCCESS;
            String redirectUrl = transaction.getClientReturnUrl();
            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                String separator = redirectUrl.contains("?") ? "&" : "?";
                response.sendRedirect(redirectUrl + separator + "transactionId=" + transaction.getTransactionId() + "&success=" + success);
                return;
            }

            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"transactionId\":\"" + transaction.getTransactionId() + "\",\"success\":" + success + ",\"status\":\"" + transaction.getStatus().name() + "\",\"responseCode\":\"" + transaction.getResponseCode() + "\"}");
        } catch (Exception ex) {
            ContextUtil.abortContext(request);
            throw ex;
        } finally {
            ContextUtil.completeContext(request);
        }
    }

    private String buildCallbackUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getScheme()).append("://");
        url.append(request.getServerName());
        if ((request.getScheme().equals("http") && request.getServerPort() != 80)
            || (request.getScheme().equals("https") && request.getServerPort() != 443)) {
            url.append(":").append(request.getServerPort());
        }
        url.append(request.getContextPath()).append("/api/vnpay/return");
        return url.toString();
    }

    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    public static class VnpayCreatePaymentResponse {
        private String paymentUrl;
        private String transactionId;

        public String getPaymentUrl() {
            return paymentUrl;
        }

        public void setPaymentUrl(String paymentUrl) {
            this.paymentUrl = paymentUrl;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }
    }

    public static class VnpayStatusResponse {
        private String transactionId;
        private boolean success;
        private String status;
        private String responseCode;

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getResponseCode() {
            return responseCode;
        }

        public void setResponseCode(String responseCode) {
            this.responseCode = responseCode;
        }
    }
}

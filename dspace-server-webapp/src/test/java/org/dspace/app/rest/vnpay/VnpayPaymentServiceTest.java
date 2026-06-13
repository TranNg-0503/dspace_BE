/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.vnpay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.dspace.app.rest.vnpay.dao.VnpayTransactionDAO;
import org.dspace.core.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class VnpayPaymentServiceTest {

    @Mock
    private VnpayTransactionDAO vnpayTransactionDAO;

    private Context context;

    private VnpayPaymentService vnpayPaymentService;

    @Before
    public void setUp() {
        vnpayPaymentService = new VnpayPaymentService(
            vnpayTransactionDAO,
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "test-tmn-code",
            "test-hash-secret"
        );
    }

    @Test
    public void createTransactionPersistsTransaction() throws Exception {
        UUID bitstreamId = UUID.randomUUID();
        VnpayCreatePaymentRequest request = new VnpayCreatePaymentRequest();
        request.setBitstreamId(bitstreamId.toString());
        request.setAmount(20000L);
        request.setOrderInfo("Test payment");
        request.setReturnUrl("http://localhost/return");

        when(vnpayTransactionDAO.create(eq(context), any(VnpayTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));

        VnpayTransaction transaction =
            vnpayPaymentService.createTransaction(context, request, "http://localhost/api/vnpay/return");

        assertEquals(bitstreamId, transaction.getBitstreamId());
        assertEquals(VnpayTransactionStatus.PENDING, transaction.getStatus());
        verify(vnpayTransactionDAO).create(context, transaction);
    }

    @Test
    public void getTransactionUsesTransactionReference() throws Exception {
        VnpayTransaction transaction = new VnpayTransaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        when(vnpayTransactionDAO.findByTransactionId(context, transaction.getTransactionId()))
            .thenReturn(transaction);

        VnpayTransaction result =
            vnpayPaymentService.getTransaction(context, transaction.getTransactionId());

        assertSame(transaction, result);
    }

    @Test
    public void updateTransactionStatusPersistsFailedHashResult() throws Exception {
        VnpayTransaction transaction = new VnpayTransaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setStatus(VnpayTransactionStatus.PENDING);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_TxnRef", transaction.getTransactionId());
        queryParams.put("vnp_ResponseCode", "00");

        when(vnpayTransactionDAO.findByTransactionId(context, transaction.getTransactionId()))
            .thenReturn(transaction);

        VnpayTransaction result = vnpayPaymentService.updateTransactionStatus(context, queryParams);

        assertSame(transaction, result);
        assertEquals(VnpayTransactionStatus.FAILED, result.getStatus());
        verify(vnpayTransactionDAO).save(context, transaction);
    }
}

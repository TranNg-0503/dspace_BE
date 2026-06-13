/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.vnpay.dao.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

import org.dspace.AbstractUnitTest;
import org.dspace.app.rest.vnpay.VnpayTransaction;
import org.dspace.app.rest.vnpay.VnpayTransactionStatus;
import org.dspace.app.rest.vnpay.dao.VnpayTransactionDAO;
import org.junit.Test;

public class VnpayTransactionDAOImplTest extends AbstractUnitTest {

    private final VnpayTransactionDAO vnpayTransactionDAO = new VnpayTransactionDAOImpl();

    @Test
    public void findByTransactionIdReturnsPersistedTransaction() throws Exception {
        VnpayTransaction transaction = new VnpayTransaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setAmount(new BigDecimal("20000"));
        transaction.setStatus(VnpayTransactionStatus.PENDING);
        transaction.setCreatedAt(new Date());

        VnpayTransaction persistedTransaction = vnpayTransactionDAO.create(context, transaction);
        VnpayTransaction foundTransaction =
            vnpayTransactionDAO.findByTransactionId(context, transaction.getTransactionId());

        assertSame(persistedTransaction, foundTransaction);
        assertEquals(transaction.getTransactionId(), foundTransaction.getTransactionId());
        assertEquals(VnpayTransactionStatus.PENDING, foundTransaction.getStatus());
    }

    @Test
    public void findByTransactionIdReturnsNullForUnknownTransaction() throws Exception {
        assertNull(vnpayTransactionDAO.findByTransactionId(context, UUID.randomUUID().toString()));
    }
}

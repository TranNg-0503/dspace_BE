/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.vnpay.dao;

import java.sql.SQLException;

import org.dspace.app.rest.vnpay.VnpayTransaction;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;

/**
 * Data access object for VNPay transactions.
 */
public interface VnpayTransactionDAO extends GenericDAO<VnpayTransaction> {

    /**
     * Find a VNPay transaction by the identifier sent as {@code vnp_TxnRef}.
     *
     * @param context current DSpace context
     * @param transactionId VNPay transaction reference
     * @return matching transaction, or {@code null} when none exists
     * @throws SQLException if a database error occurs
     */
    VnpayTransaction findByTransactionId(Context context, String transactionId) throws SQLException;
}

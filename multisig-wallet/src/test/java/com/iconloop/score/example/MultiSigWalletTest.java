/*
 * Copyright 2020 ICONLOOP Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iconloop.score.example;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import score.Address;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class MultiSigWalletTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private Account[] owners;
    private Score multisigScore;
    private MultiSigWallet multisigSpy;

    @BeforeEach
    void setup() throws Exception {
        // setup accounts and deploy
        owners = new Account[3];
        for (int i = 0; i < owners.length; i++) {
            owners[i] = sm.createAccount(100);
        }
        String initialOwners = Arrays.stream(owners)
                .map(a -> a.getAddress().toString())
                .collect(Collectors.joining(","));
        multisigScore = sm.deploy(owners[0], MultiSigWallet.class,
                initialOwners, BigInteger.TWO);

        // setup spy
        multisigSpy = (MultiSigWallet) spy(multisigScore.getInstance());
        multisigScore.setInstance(multisigSpy);
    }

    @Test
    void fallback() {
        sm.transfer(owners[0], multisigScore.getAddress(), ICX);
        verify(multisigSpy).Deposit(owners[0].getAddress(), ICX);
    }

    @Test
    void tokenFallback() {
        byte[] data = "test".getBytes();
        multisigScore.invoke(owners[0], "tokenFallback", owners[0].getAddress(), ICX, data);
        verify(multisigSpy).DepositToken(owners[0].getAddress(), ICX, data);
    }

    @Test
    void addWalletOwner() {
        // add new wallet owner
        Account alice = sm.createAccount();
        BigInteger txId = submitAddWalletOwner(owners[0], alice);
        // confirmation from another owner
        confirmByOwner(owners[1], txId);

        verify(multisigSpy).Execution(txId);
        verify(multisigSpy).addWalletOwner(alice.getAddress());
        verify(multisigSpy).WalletOwnerAddition(alice.getAddress());

        @SuppressWarnings("unchecked")
        var walletOwners = (List<Address>) multisigScore.call("getWalletOwners");
        assertEquals(4, walletOwners.size());
    }

    private BigInteger submitAddWalletOwner(Account submitter, Account acct) {
        String params = String.format("[{\"name\": \"_walletOwner\", \"type\": \"Address\", \"value\": \"%s\"}]",
                acct.getAddress());
        multisigScore.invoke(submitter, "submitTransaction",
                multisigScore.getAddress(), "addWalletOwner", params, BigInteger.ZERO, "");
        ArgumentCaptor<BigInteger> txId = ArgumentCaptor.forClass(BigInteger.class);
        verify(multisigSpy).Submission(txId.capture());
        verify(multisigSpy).Confirmation(submitter.getAddress(), txId.getValue());
        return txId.getValue();
    }

    private void confirmByOwner(Account acct, BigInteger txId) {
        multisigScore.invoke(acct, "confirmTransaction", txId);
        verify(multisigSpy).Confirmation(acct.getAddress(), txId);
    }

    @Test
    void removeWalletOwner() {
        // remove 1st wallet
        String params = String.format("[{\"name\": \"_walletOwner\", \"type\": \"Address\", \"value\": \"%s\"}]",
                owners[0].getAddress());
        multisigScore.invoke(owners[1], "submitTransaction",
                multisigScore.getAddress(), "removeWalletOwner", params, BigInteger.ZERO, "");
        ArgumentCaptor<BigInteger> txId = ArgumentCaptor.forClass(BigInteger.class);
        verify(multisigSpy).Submission(txId.capture());
        verify(multisigSpy).Confirmation(owners[1].getAddress(), txId.getValue());

        // confirmation from another owner
        confirmByOwner(owners[2], txId.getValue());

        verify(multisigSpy).Execution(txId.getValue());
        verify(multisigSpy).removeWalletOwner(owners[0].getAddress());
        verify(multisigSpy).WalletOwnerRemoval(owners[0].getAddress());

        @SuppressWarnings("unchecked")
        var walletOwners = (List<Address>) multisigScore.call("getWalletOwners");
        assertEquals(2, walletOwners.size());
    }

    @Test
    void replaceWalletOwner() {
        // replace 2nd wallet with new owner
        Account alice = sm.createAccount();
        String params = String.format(
                "[{\"name\": \"_walletOwner\", \"type\": \"Address\", \"value\": \"%s\"},"
                + "{\"name\": \"_newWalletOwner\", \"type\": \"Address\", \"value\": \"%s\"}]",
                owners[1].getAddress(), alice.getAddress());
        multisigScore.invoke(owners[0], "submitTransaction",
                multisigScore.getAddress(), "replaceWalletOwner", params, BigInteger.ZERO, "");
        ArgumentCaptor<BigInteger> txId = ArgumentCaptor.forClass(BigInteger.class);
        verify(multisigSpy).Submission(txId.capture());
        verify(multisigSpy).Confirmation(owners[0].getAddress(), txId.getValue());

        // confirmation from another owner
        confirmByOwner(owners[2], txId.getValue());

        verify(multisigSpy).Execution(txId.getValue());
        verify(multisigSpy).replaceWalletOwner(owners[1].getAddress(), alice.getAddress());
        verify(multisigSpy).WalletOwnerAddition(alice.getAddress());
        verify(multisigSpy).WalletOwnerRemoval(owners[1].getAddress());

        @SuppressWarnings("unchecked")
        var walletOwners = (List<Address>) multisigScore.call("getWalletOwners");
        assertEquals(3, walletOwners.size());
    }

    @Test
    void changeRequirement() {
        // check the current requirement first
        BigInteger oldReq = (BigInteger) multisigScore.call("getRequirement");
        assertEquals(2, oldReq.intValue());

        // change requirement
        var newReq = oldReq.add(BigInteger.ONE);
        String params = String.format("[{\"name\": \"_required\", \"type\": \"int\", \"value\": \"%d\"}]", newReq);
        multisigScore.invoke(owners[0], "submitTransaction",
                multisigScore.getAddress(), "changeRequirement", params, BigInteger.ZERO, "");
        ArgumentCaptor<BigInteger> txId = ArgumentCaptor.forClass(BigInteger.class);
        verify(multisigSpy).Submission(txId.capture());
        verify(multisigSpy).Confirmation(owners[0].getAddress(), txId.getValue());

        // confirmation from another owner
        confirmByOwner(owners[1], txId.getValue());

        verify(multisigSpy).Execution(txId.getValue());
        verify(multisigSpy).changeRequirement(newReq);
        verify(multisigSpy).RequirementChange(newReq);

        assertEquals(newReq, multisigScore.call("getRequirement"));
    }

    @Test
    void confirmTransaction_notOwner() {
        // add new wallet owner first
        Account alice = sm.createAccount();
        BigInteger txId = submitAddWalletOwner(owners[0], alice);
        // confirm by alice herself
        assertThrows(AssertionError.class, () ->
                multisigScore.invoke(alice, "confirmTransaction", txId));
    }

    @Test
    void revokeTransaction() {
        // add new wallet owner and confirm first
        BigInteger txId = submitAddWalletOwner(owners[0], sm.createAccount());
        assertEquals(1, getConfirmationCount(txId));

        // revoke the transaction
        multisigScore.invoke(owners[0], "revokeTransaction", txId);
        verify(multisigSpy).Revocation(owners[0].getAddress(), txId);
        assertEquals(0, getConfirmationCount(txId));
    }

    private int getConfirmationCount(BigInteger txId) {
        return (int) multisigScore.call("getConfirmationCount", txId);
    }

    @Test
    void getConfirmations() {
        // add new wallet owner and confirm first
        BigInteger txId = submitAddWalletOwner(owners[2], sm.createAccount());
        assertEquals(1, getConfirmationCount(txId));

        // confirmation from another owner
        confirmByOwner(owners[1], txId);
        assertEquals(2, getConfirmationCount(txId));

        // get confirmations
        @SuppressWarnings("unchecked")
        var confirmations = (List<Address>) multisigScore.call("getConfirmations", txId);
        var sortedOwners = confirmations.stream()
                .map(Address::toString)
                .sorted()
                .collect(Collectors.joining(","));
        var expected = List.of(owners[1].getAddress(), owners[2].getAddress())
                .stream()
                .map(Address::toString)
                .sorted()
                .collect(Collectors.joining(","));
        assertEquals(expected, sortedOwners);
    }

    @Test
    void getTransactionCount() {
        // submit dummy transactions
        int count = 5;
        for (int i = 0; i < count; i++) {
            multisigScore.invoke(owners[0], "submitTransaction", owners[0].getAddress(), "", "", BigInteger.ZERO, "");
        }
        // verify pending transactions
        var txCount = (BigInteger) multisigScore.call("getTransactionCount", true, false);
        assertEquals(count, txCount.intValue());

        // confirm two transactions
        multisigScore.invoke(owners[1], "confirmTransaction", BigInteger.ZERO);
        multisigScore.invoke(owners[1], "confirmTransaction", BigInteger.ONE);

        // verify executed transactions
        txCount = (BigInteger) multisigScore.call("getTransactionCount", false, true);
        assertEquals(2, txCount.intValue());
        // verify total transactions
        txCount = (BigInteger) multisigScore.call("getTransactionCount", true, true);
        assertEquals(count, txCount.intValue());
    }

    @Test
    void getTransactionInfo() {
        // add new wallet owner
        Account alice = sm.createAccount();
        BigInteger txId = submitAddWalletOwner(owners[0], alice);

        @SuppressWarnings("unchecked")
        var txInfo = (Map<String, String>) multisigScore.call("getTransactionInfo", txId);
        assertEquals(txInfo.get("_transactionId"), "0x0");
        assertEquals(txInfo.get("_method"), "addWalletOwner");
        assertEquals(txInfo.get("_destination"), multisigScore.getAddress().toString());
        assertEquals(txInfo.get("_executed"), "0x0");

        // confirmation from another owner
        confirmByOwner(owners[1], txId);

        @SuppressWarnings("unchecked")
        var txInfo2 = (Map<String, String>) multisigScore.call("getTransactionInfo", txId);
        assertEquals(txInfo2.get("_executed"), "0x1");
    }

    @Test
    void getTransactionIds() {
        // submit dummy transactions
        int count = 5;
        for (int i = 0; i < count; i++) {
            multisigScore.invoke(owners[0], "submitTransaction", owners[0].getAddress(), "", "", BigInteger.ZERO, "");
        }

        // get total transaction ids
        @SuppressWarnings("unchecked")
        var txIds = (List<BigInteger>) multisigScore.call("getTransactionIds",
                BigInteger.valueOf(0), BigInteger.valueOf(count), true, true);
        assertEquals(count, txIds.size());

        // confirm two transactions
        multisigScore.invoke(owners[1], "confirmTransaction", BigInteger.ZERO);
        multisigScore.invoke(owners[1], "confirmTransaction", BigInteger.TWO);

        // get executed transaction ids
        @SuppressWarnings("unchecked")
        var txIds2 = (List<BigInteger>) multisigScore.call("getTransactionIds",
                BigInteger.valueOf(0), BigInteger.valueOf(count), false, true);
        assertArrayEquals(
                new BigInteger[]{BigInteger.ZERO, BigInteger.TWO},
                txIds2.toArray(new BigInteger[0]));

        // get pending transaction ids
        @SuppressWarnings("unchecked")
        var txIds3 = (List<BigInteger>) multisigScore.call("getTransactionIds",
                BigInteger.valueOf(0), BigInteger.valueOf(count), true, false);
        assertArrayEquals(
                new BigInteger[]{BigInteger.ONE, BigInteger.valueOf(3), BigInteger.valueOf(4)},
                txIds3.toArray(new BigInteger[0]));
    }

    @Test
    void getTransactionList() {
        // submit dummy transactions
        int count = 5;
        for (int i = 0; i < count; i++) {
            multisigScore.invoke(owners[0], "submitTransaction", owners[0].getAddress(), "", "", BigInteger.ZERO, "");
        }

        // confirm two transactions
        multisigScore.invoke(owners[1], "confirmTransaction", BigInteger.ONE);
        multisigScore.invoke(owners[1], "confirmTransaction", BigInteger.TWO);

        // get executed transaction list
        @SuppressWarnings("unchecked")
        var txList = (List<Map<String, String>>) multisigScore.call("getTransactionList",
                BigInteger.valueOf(0), BigInteger.valueOf(count), false, true);
        var received = txList.stream()
                .map((tx) -> tx.get("_transactionId"))
                .collect(Collectors.joining(","));
        assertEquals("0x1,0x2", received);

        // get pending transaction list
        @SuppressWarnings("unchecked")
        var txList2 = (List<Map<String, String>>) multisigScore.call("getTransactionList",
                BigInteger.valueOf(0), BigInteger.valueOf(count), true, false);
        received = txList2.stream()
                .map((tx) -> tx.get("_transactionId"))
                .collect(Collectors.joining(","));
        assertEquals("0x0,0x3,0x4", received);
    }

    @Test
    void getConvertedParams_wrongFormat() {
        String[] wrongFormats = {
                "{\"name\": \"_walletOwner\", \"type\": \"Address\", \"value\": \"%s\"}",
                "[{\"name\": \"_walletOwner\", \"type\": \"Address\", \"value\": \"%s\"}",
                "[{\"name\": \"_walletOwner\", \"type\": \"String\", \"value\": \"%s\"}]",
                "[{\"name\": \"_walletOwner\", \"type_\": \"Address\", \"value\": \"%s\"}]",
                "[{\"name\": \"_walletOwner\", \"type\": \"%s\"}]",
                "[{\"name\": \"_walletOwner\", \"value\": \"%s\"}]",
                "[{\"type\": \"_walletOwner\", \"type\": \"Address\", \"value\": \"%s\"}]",
        };
        BigInteger txId = BigInteger.ZERO;
        for (String format : wrongFormats) {
            String params = String.format(format, sm.createAccount().getAddress());
            System.out.println(">>> params=" + params);
            multisigScore.invoke(owners[0], "submitTransaction",
                    multisigScore.getAddress(), "addWalletOwner", params, BigInteger.ZERO, "");
            confirmByOwner(owners[1], txId);

            verify(multisigSpy).ExecutionFailure(txId);
            reset(multisigSpy);
            txId = txId.add(BigInteger.ONE);
        }
    }
}

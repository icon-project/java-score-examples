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

import java.math.BigInteger;

import static java.math.BigInteger.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class SampleCrowdsaleTest extends TestBase {
    // sample-token
    private static final String name = "MySampleToken";
    private static final String symbol = "MST";
    private static final int decimals = 18;
    private static final BigInteger initialSupply = BigInteger.valueOf(1000);
    private static final BigInteger totalSupply = initialSupply.multiply(TEN.pow(decimals));

    // sample-crowdsale
    private static final BigInteger fundingGoalInICX = BigInteger.valueOf(100);
    private static final BigInteger durationInBlocks = BigInteger.valueOf(32);

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private Score tokenScore;
    private Score crowdsaleScore;

    private SampleCrowdsale crowdsaleSpy;
    private final byte[] startCrowdsaleBytes = "start crowdsale".getBytes();

    @BeforeEach
    public void setup() throws Exception {
        // deploy token and crowdsale scores
        tokenScore = sm.deploy(owner, IRC2BasicToken.class,
                name, symbol, decimals, initialSupply);
        crowdsaleScore = sm.deploy(owner, SampleCrowdsale.class,
                fundingGoalInICX, tokenScore.getAddress(), durationInBlocks);

        // setup spy object against the crowdsale object
        crowdsaleSpy = (SampleCrowdsale) spy(crowdsaleScore.getInstance());
        crowdsaleScore.setInstance(crowdsaleSpy);
    }

    private void startCrowdsale() {
        // transfer all tokens to crowdsale score
        tokenScore.invoke(owner, "transfer", crowdsaleScore.getAddress(), totalSupply, startCrowdsaleBytes);
    }

    @Test
    void tokenFallback() {
        startCrowdsale();
        // verify
        verify(crowdsaleSpy).tokenFallback(owner.getAddress(), totalSupply, startCrowdsaleBytes);
        verify(crowdsaleSpy).CrowdsaleStarted(eq(ICX.multiply(fundingGoalInICX)), anyLong());
        assertEquals(totalSupply, tokenScore.call("balanceOf", crowdsaleScore.getAddress()));
    }

    @Test
    void fallback_crowdsaleNotYetStarted() {
        Account alice = sm.createAccount(100);
        BigInteger fund = ICX.multiply(BigInteger.valueOf(40));
        // crowdsale is not yet started
        assertThrows(AssertionError.class, () ->
                sm.transfer(alice, crowdsaleScore.getAddress(), fund));
    }

    @Test
    void fallback() {
        startCrowdsale();
        // fund 40 icx from Alice
        Account alice = sm.createAccount(100);
        BigInteger fund = ICX.multiply(BigInteger.valueOf(40));
        sm.transfer(alice, crowdsaleScore.getAddress(), fund);
        // verify
        verify(crowdsaleSpy).fallback();
        verify(crowdsaleSpy).FundTransfer(alice.getAddress(), fund, true);
        assertEquals(fund, Account.getAccount(crowdsaleScore.getAddress()).getBalance());
    }

    @Test
    void checkGoalReached() {
        startCrowdsale();
        // increase the block height
        sm.getBlock().increase(durationInBlocks.longValue());
        crowdsaleScore.invoke(owner, "checkGoalReached");
        // verify
        verify(crowdsaleSpy).CrowdsaleEnded();
        verify(crowdsaleSpy, never()).GoalReached(any(), any());
    }

    @Test
    void safeWithdrawal() {
        startCrowdsale();
        // fund 40 icx from Alice
        Account alice = sm.createAccount(100);
        sm.transfer(alice, crowdsaleScore.getAddress(), ICX.multiply(BigInteger.valueOf(40)));
        // fund 60 icx from Bob
        Account bob = sm.createAccount(100);
        sm.transfer(bob, crowdsaleScore.getAddress(), ICX.multiply(BigInteger.valueOf(60)));
        // make the goal reached
        sm.getBlock().increase(durationInBlocks.longValue());
        crowdsaleScore.invoke(owner, "checkGoalReached");
        // invoke safeWithdrawal
        crowdsaleScore.invoke(owner, "safeWithdrawal");
        // verify
        verify(crowdsaleSpy).GoalReached(owner.getAddress(), ICX.multiply(fundingGoalInICX));
        verify(crowdsaleSpy).FundTransfer(owner.getAddress(), ICX.multiply(fundingGoalInICX), false);
        assertEquals(ICX.multiply(fundingGoalInICX), Account.getAccount(owner.getAddress()).getBalance());
    }

    @Test
    void safeWithdrawal_refund() {
        startCrowdsale();
        // fund 40 icx from Alice
        Account alice = sm.createAccount(100);
        sm.transfer(alice, crowdsaleScore.getAddress(), ICX.multiply(BigInteger.valueOf(40)));
        // fund 50 icx from Bob
        Account bob = sm.createAccount(100);
        sm.transfer(bob, crowdsaleScore.getAddress(), ICX.multiply(BigInteger.valueOf(50)));
        // make the goal reached
        sm.getBlock().increase(durationInBlocks.longValue());
        crowdsaleScore.invoke(owner, "checkGoalReached");
        verify(crowdsaleSpy, never()).GoalReached(owner.getAddress(), ICX.multiply(fundingGoalInICX));

        // invoke safeWithdrawal from alice to refund
        crowdsaleScore.invoke(alice, "safeWithdrawal");
        verify(crowdsaleSpy).FundTransfer(alice.getAddress(), ICX.multiply(BigInteger.valueOf(40)), false);
        // invoke safeWithdrawal from bob to refund
        crowdsaleScore.invoke(bob, "safeWithdrawal");
        verify(crowdsaleSpy).FundTransfer(bob.getAddress(), ICX.multiply(BigInteger.valueOf(50)), false);
    }
}

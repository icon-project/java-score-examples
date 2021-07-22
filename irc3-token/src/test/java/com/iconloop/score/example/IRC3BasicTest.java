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
import score.Address;

import java.math.BigInteger;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IRC3BasicTest extends TestBase {
    private static final Address ZERO_ADDRESS =  new Address(new byte[Address.LENGTH]);
    private static final String name = "MyIRC3Token";
    private static final String symbol = "NFT";

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private Score tokenScore;
    private final SecureRandom secureRandom = new SecureRandom();

    @BeforeEach
    public void setup() throws Exception {
        tokenScore = sm.deploy(owner, IRC3BasicToken.class, name, symbol);
    }

    @Test
    void name() {
        assertEquals(name, tokenScore.call("name"));
    }

    @Test
    void symbol() {
        assertEquals(symbol, tokenScore.call("symbol"));
    }

    private BigInteger getTokenId() {
        byte[] bytes = new byte[8];
        secureRandom.nextBytes(bytes);
        bytes[0] = 0; // make positive
        return new BigInteger(bytes);
    }

    private BigInteger mintToken() {
        int supply = (int) tokenScore.call("totalSupply");
        var tokenId = getTokenId();
        tokenScore.invoke(owner, "mint", tokenId);
        assertEquals(supply + 1, tokenScore.call("totalSupply"));
        return tokenId;
    }

    @Test
    void balanceOf() {
        mintToken();
        assertEquals(1, tokenScore.call("balanceOf", owner.getAddress()));
    }

    @Test
    void ownerOf() {
        var tokenId = mintToken();
        assertEquals(owner.getAddress(), tokenScore.call("ownerOf", tokenId));
    }

    @Test
    void approve() {
        var tokenId = mintToken();
        var alice = sm.createAccount();
        approveToken(owner, alice.getAddress(), tokenId);
    }

    private void approveToken(Account owner, Address to, BigInteger tokenId) {
        assertEquals(ZERO_ADDRESS, tokenScore.call("getApproved", tokenId));
        tokenScore.invoke(owner, "approve", to, tokenId);
        assertEquals(owner.getAddress(), tokenScore.call("ownerOf", tokenId));
        assertEquals(to, tokenScore.call("getApproved", tokenId));
    }

    @Test
    void transfer() {
        var tokenId = mintToken();
        var alice = sm.createAccount();
        tokenScore.invoke(owner, "transfer", alice.getAddress(), tokenId);
        assertEquals(alice.getAddress(), tokenScore.call("ownerOf", tokenId));
    }

    @Test
    void transferFrom() {
        var tokenId = mintToken();
        var alice = sm.createAccount();
        var bob = sm.createAccount();
        assertThrows(AssertionError.class, () ->
                tokenScore.invoke(alice, "transferFrom", owner.getAddress(), bob.getAddress(), tokenId));
        approveToken(owner, alice.getAddress(), tokenId);
        assertDoesNotThrow(() ->
                tokenScore.invoke(alice, "transferFrom", owner.getAddress(), bob.getAddress(), tokenId));
        assertEquals(bob.getAddress(), tokenScore.call("ownerOf", tokenId));
        assertEquals(ZERO_ADDRESS, tokenScore.call("getApproved", tokenId));
        assertDoesNotThrow(() ->
                tokenScore.invoke(bob, "transferFrom", bob.getAddress(), alice.getAddress(), tokenId));
        assertEquals(alice.getAddress(), tokenScore.call("ownerOf", tokenId));
    }

    @Test
    void burn() {
        var tokenId = mintToken();
        var tokenId2 = mintToken();
        var alice = sm.createAccount();
        tokenScore.invoke(owner, "transfer", alice.getAddress(), tokenId);
        assertThrows(AssertionError.class, () ->
                tokenScore.invoke(owner, "burn", tokenId));
        assertDoesNotThrow(() ->
                tokenScore.invoke(alice, "burn", tokenId));
        assertEquals(1, tokenScore.call("totalSupply"));
        tokenScore.invoke(owner, "burn", tokenId2);
        assertEquals(0, tokenScore.call("totalSupply"));
    }
}

/*
 * Copyright 2020 ICON Foundation
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

package foundation.icon.test.score;

import foundation.icon.icx.data.Address;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.Constants;
import foundation.icon.test.TransactionHandler;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ChainScore extends Score {
    private static final int CONFIG_AUDIT = 0x2;

    public ChainScore(TransactionHandler txHandler) {
        super(txHandler, Constants.SYSTEM_ADDRESS);
    }

    public int getRevision() throws IOException {
        return call("getRevision", null).asInteger().intValue();
    }

    public BigInteger getStepPrice() throws IOException {
        return call("getStepPrice", null).asInteger();
    }

    public int getServiceConfig() throws IOException {
        return call("getServiceConfig", null).asInteger().intValue();
    }

    public Map<String, BigInteger> getStepCosts() throws Exception {
        RpcItem rpcItem = call("getStepCosts", null);
        Map<String, BigInteger> map = new HashMap<>();
        Set<String> stepCostTypes = rpcItem.asObject().keySet();
        for (String type: stepCostTypes) {
            map.put(type, rpcItem.asObject().getItem(type).asInteger());
        }
        return map;
    }

    public static boolean isAuditEnabled(int config) {
        return (config & CONFIG_AUDIT) != 0;
    }

    public boolean isAuditEnabled() throws IOException {
        return isAuditEnabled(this.getServiceConfig());
    }

    public RpcObject getScoreStatus(Address address) throws IOException {
        RpcObject params = new RpcObject.Builder()
                .put("address", new RpcValue(address))
                .build();
        return call("getScoreStatus", params).asObject();
    }
}

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

package com.iconloop.testsvc;

import score.Address;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class ServiceManager {
    private final Map<Class<?>, Score> scoreMap = new HashMap<>();
    private int nextCount = 1;

    public Score deploy(Account owner, Class<?> mainClass, Object... params) throws Exception {
        var score = new Score(Account.newScoreAccount(nextCount++), owner);
        scoreMap.put(mainClass, score);
        pushContext("<init>", false);
        try {
            Constructor<?> ctor = getConstructor(mainClass, params);
            score.setInstance(ctor.newInstance(params));
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
            throw e;
        } finally {
            popContext();
        }
        return score;
    }

    public Account createAccount() {
        return Account.newExternalAccount(nextCount++);
    }

    public Address getOwner(Class<?> key) {
        var score = scoreMap.get(key);
        if (score == null) {
            throw new IllegalStateException();
        }
        return score.getOwner().getAddress();
    }

    public Address getOrigin(Class<?> key) {
        return getOwner(key);
    }

    public Address getCaller(Class<?> key) {
        return getOwner(key);
    }

    public Address getAddress(Class<?> key) {
        var score = scoreMap.get(key);
        if (score == null) {
            throw new IllegalStateException();
        }
        return score.getAddress();
    }

    private Constructor<?> getConstructor(Class<?> mainClass, Object[] params) throws NoSuchMethodException {
        Class<?>[] paramClasses = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            paramClasses[i] = params[i].getClass();
        }
        return mainClass.getConstructor(paramClasses);
    }

    private final Stack<Frame> context = new Stack<>();

    static class Frame {
        String method;
        boolean readonly;

        public Frame(String method, boolean readonly) {
            this.method = method;
            this.readonly = readonly;
        }
    }

    protected void pushContext(String method, boolean readonly) {
        context.push(new Frame(method, readonly));
    }

    protected void popContext() {
        context.pop();
    }

    public boolean getCurrentContext() {
        return context.peek().readonly;
    }
}

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

package score.impl;

import com.iconloop.testsvc.ServiceManager;
import com.iconloop.testsvc.TestBase;
import score.Address;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class AnyDBImpl extends TestBase implements AnyDB {
    private static final ServiceManager sm = getServiceManager();
    private final String prefix;
    private final Class<?> leafValue;

    private enum Type {
        ArrayDB,
        DictDB,
        VarDB;
    }

    public AnyDBImpl(String id, Class<?> valueClass) {
        this.prefix = id;
        this.leafValue = valueClass;
    }

    private String getSubId(Object key) {
        return this.prefix + encodeKey(key);
    }

    private String encodeKey(Object v) {
        if (v == null) {
            return "";
        } else if (v instanceof String) {
            return (String) v;
        }
        return new String(encodeValue(v));
    }

    private byte[] encodeValue(Object v) {
        if (v == null) {
            return null;
        } else if (v instanceof byte[]) {
            return (byte[]) v;
        } else if (v instanceof Boolean) {
            return BigInteger.valueOf((Boolean) v ? 1 : 0).toByteArray();
        } else if (v instanceof Byte) {
            return BigInteger.valueOf((Byte) v).toByteArray();
        } else if (v instanceof Short) {
            return BigInteger.valueOf((Short) v).toByteArray();
        } else if (v instanceof Character) {
            return BigInteger.valueOf((Character) v).toByteArray();
        } else if (v instanceof Integer) {
            return BigInteger.valueOf((Integer) v).toByteArray();
        } else if (v instanceof Long) {
            return BigInteger.valueOf((Long) v).toByteArray();
        } else if (v instanceof BigInteger) {
            return ((BigInteger) v).toByteArray();
        } else if (v instanceof Address) {
            return ((Address) v).toByteArray();
        } else if (v instanceof String) {
            return ((String) v).getBytes(StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + v.getClass());
        }
    }

    private Object decodeValue(byte[] raw, Class<?> cls) {
        if (raw == null) {
            return null;
        } else if (cls == byte[].class) {
            return new BigInteger(raw).toByteArray();
        } else if (cls == Boolean.class) {
            return new BigInteger(raw).intValue() != 0;
        } else if (cls == Byte.class) {
            return new BigInteger(raw).byteValue();
        } else if (cls == Short.class) {
            return new BigInteger(raw).shortValue();
        } else if (cls == Integer.class || cls == Character.class) {
            return new BigInteger(raw).intValue();
        } else if (cls == Long.class) {
            return new BigInteger(raw).longValue();
        } else if (cls == BigInteger.class) {
            return new BigInteger(raw);
        } else if (cls == Address.class) {
            return new Address(raw);
        } else if (cls == String.class) {
            return new String(raw, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + cls);
        }
    }

    private String getStorageKey(Object k, Type type) {
        return type.name() + getSubId(k);
    }

    private void setValue(String key, byte[] value) {
        sm.putStorage(key, value);
    }

    private byte[] getValue(String key) {
        return sm.getStorage(key);
    }

    // DictDB
    @Override
    public void set(Object key, Object value) {
        if (sm.getCurrentFrame().isReadonly()) {
            throw new IllegalStateException("read-only context");
        }
        setValue(getStorageKey(key, Type.DictDB), encodeValue(value));
    }

    @Override
    public Object get(Object key) {
        return decodeValue(getValue(getStorageKey(key, Type.DictDB)), leafValue);
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        var v = decodeValue(getValue(getStorageKey(key, Type.DictDB)), leafValue);
        return (v != null) ? v : defaultValue;
    }

    // BranchDB
    @Override
    public Object at(Object key) {
        return new AnyDBImpl(getSubId(key), leafValue);
    }

    // ArrayDB
    @Override
    public void add(Object value) {
        if (sm.getCurrentFrame().isReadonly()) {
            throw new IllegalStateException("read-only context");
        }
        int size = size();
        setValue(getStorageKey(size, Type.ArrayDB), encodeValue(value));
        setValue(getStorageKey(null, Type.ArrayDB), encodeValue(size + 1));
    }

    @Override
    public void set(int index, Object value) {
        if (sm.getCurrentFrame().isReadonly()) {
            throw new IllegalStateException("read-only context");
        }
        int size = size();
        if (index >= size || index < 0) {
            throw new IllegalArgumentException();
        }
        setValue(getStorageKey(index, Type.ArrayDB), encodeValue(value));
    }

    @Override
    public void removeLast() {
        pop();
    }

    @Override
    public Object get(int index) {
        int size = size();
        if (index >= size || index < 0) {
            throw new IllegalArgumentException();
        }
        return decodeValue(getValue(getStorageKey(index, Type.ArrayDB)), leafValue);
    }

    @Override
    public int size() {
        var v = getValue(getStorageKey(null, Type.ArrayDB));
        if (v == null) return 0;
        return new BigInteger(v).intValue();
    }

    @Override
    public Object pop() {
        if (sm.getCurrentFrame().isReadonly()) {
            throw new IllegalStateException("read-only context");
        }
        int size = size();
        if (size <= 0) {
            throw new IllegalArgumentException();
        }
        var v = decodeValue(getValue(getStorageKey(size - 1, Type.ArrayDB)), leafValue);
        setValue(getStorageKey(size - 1, Type.ArrayDB), null);
        setValue(getStorageKey(null, Type.ArrayDB), encodeValue(size - 1));
        return v;
    }

    // VarDB
    @Override
    public void set(Object value) {
        if (sm.getCurrentFrame().isReadonly()) {
            throw new IllegalStateException("read-only context");
        }
        setValue(getStorageKey(null, Type.VarDB), encodeValue(value));
    }

    @Override
    public Object get() {
        return decodeValue(getValue(getStorageKey(null, Type.VarDB)), leafValue);
    }

    @Override
    public Object getOrDefault(Object defaultValue) {
        var v = decodeValue(getValue(getStorageKey(null, Type.VarDB)), leafValue);
        return (v != null) ? v : defaultValue;
    }
}

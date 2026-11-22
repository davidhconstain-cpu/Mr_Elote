package com.vivanetpay.integration.gmail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import com.vivanetpay.security.SecretStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link com.google.api.client.util.store.DataStoreFactory} respaldado en {@link SecretStore}: los tokens OAuth
 * (refresh/access) nunca tocan disco en claro ni se registran en logs (RNF-002). Cada DataStore se guarda
 * como un único secreto con clave {@code <prefix>datastore.<id>}.
 */
public final class SecretStoreDataStoreFactory extends AbstractDataStoreFactory {

    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * Lista blanca ESTRICTA de clases (clases exactas, sin comodines de paquete) y límites de tamaño: lo que serializa
     * {@code StoredCredential} (cadenas, {@code Long} y su {@code ReentrantLock}). Cualquier otra clase (colecciones,
     * {@code Class}, proxies, gadgets...) se rechaza con {@code !*}.
     */
    static final String FILTER_PATTERN = "maxdepth=8;maxrefs=64;maxbytes=8192;maxarray=64;"
            + "com.google.api.client.auth.oauth2.StoredCredential;"
            + "java.lang.String;java.lang.Long;java.lang.Number;"
            + "java.util.concurrent.locks.ReentrantLock;"
            + "java.util.concurrent.locks.ReentrantLock$Sync;"
            + "java.util.concurrent.locks.ReentrantLock$NonfairSync;"
            + "java.util.concurrent.locks.ReentrantLock$FairSync;"
            + "java.util.concurrent.locks.AbstractQueuedSynchronizer;"
            + "java.util.concurrent.locks.AbstractOwnableSynchronizer;"
            + "!*";
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(FILTER_PATTERN);

    private final SecretStore secrets;
    private final String keyPrefix;

    /** @param keyPrefix p. ej. {@code "gmail.gmail-principal."} */
    public SecretStoreDataStoreFactory(SecretStore secrets, String keyPrefix) {
        this.secrets = secrets;
        this.keyPrefix = keyPrefix;
    }

    String keyFor(String id) {
        return keyPrefix + "datastore." + id;
    }

    /** Borra todos los datastores conocidos (revocación). */
    public void deleteAll() {
        secrets.delete(keyFor("StoredCredential"));
    }

    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) throws IOException {
        return new SecretDataStore<>(this, id);
    }

    private static final class SecretDataStore<V extends Serializable> extends AbstractDataStore<V> {
        private final SecretStoreDataStoreFactory factory;
        private final Map<String, byte[]> map = new LinkedHashMap<>();

        SecretDataStore(SecretStoreDataStoreFactory factory, String id) throws IOException {
            super(factory, id);
            this.factory = factory;
            load();
        }

        private void load() throws IOException {
            var secret = factory.secrets.get(factory.keyFor(getId()));
            if (secret.isEmpty()) {
                return;
            }
            char[] chars = secret.get();
            try {
                Map<String, String> stored = JSON.readValue(new String(chars), new TypeReference<>() { });
                for (var e : stored.entrySet()) {
                    map.put(e.getKey(), Base64.getDecoder().decode(e.getValue()));
                }
            } finally {
                Arrays.fill(chars, '\0');
            }
        }

        private void save() throws IOException {
            Map<String, String> out = new LinkedHashMap<>();
            for (var e : map.entrySet()) {
                out.put(e.getKey(), Base64.getEncoder().encodeToString(e.getValue()));
            }
            if (out.isEmpty()) {
                factory.secrets.delete(factory.keyFor(getId()));
                return;
            }
            String json = JSON.writeValueAsString(out);
            char[] chars = json.toCharArray();
            try {
                factory.secrets.put(factory.keyFor(getId()), chars);
            } finally {
                Arrays.fill(chars, '\0');
            }
        }

        private static byte[] serialize(Serializable value) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(value);
            }
            return bos.toByteArray();
        }

        @SuppressWarnings("unchecked")
        private V deserialize(byte[] bytes) throws IOException {
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                ois.setObjectInputFilter(FILTER);
                return (V) ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Dato de credencial ilegible", e);
            }
        }

        @Override
        public synchronized Set<String> keySet() {
            return new LinkedHashSet<>(map.keySet());
        }

        @Override
        public synchronized Collection<V> values() throws IOException {
            Collection<V> out = new ArrayList<>();
            for (byte[] b : map.values()) {
                out.add(deserialize(b));
            }
            return out;
        }

        @Override
        public synchronized V get(String key) throws IOException {
            if (key == null) {
                return null;
            }
            byte[] b = map.get(key);
            return b == null ? null : deserialize(b);
        }

        @Override
        public synchronized DataStore<V> set(String key, V value) throws IOException {
            map.put(key, serialize(value));
            save();
            return this;
        }

        @Override
        public synchronized DataStore<V> clear() throws IOException {
            map.clear();
            save();
            return this;
        }

        @Override
        public synchronized DataStore<V> delete(String key) throws IOException {
            map.remove(key);
            save();
            return this;
        }
    }
}

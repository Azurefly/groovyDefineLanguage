package com.pl.gdl.dataframe.datasource;

import java.util.Objects;

/**
 * Resolves secret references without putting plaintext credentials into GDL scripts.
 * External runtimes can inject a Vault/KMS implementation with DatasourceRegistry#setSecretResolver.
 */
@FunctionalInterface
public interface SecretResolver {
    String resolve(String reference);

    static SecretResolver system() {
        return reference -> {
            if (reference == null || reference.isBlank()) return null;
            String ref = reference.trim();
            if (ref.startsWith("env:")) {
                return require(ref, System.getenv(ref.substring(4)));
            }
            if (ref.startsWith("sys:")) {
                return require(ref, System.getProperty(ref.substring(4)));
            }
            throw new IllegalArgumentException("Unsupported secret reference '" + ref
                    + "'. Built-in schemes: env:, sys:");
        };
    }

    static SecretResolver fixed(java.util.Map<String, String> secrets) {
        Objects.requireNonNull(secrets, "secrets must not be null");
        return reference -> require(reference, secrets.get(reference));
    }

    private static String require(String reference, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Secret not found for reference '" + reference + "'");
        }
        return value;
    }
}

package org.torusresearch.torusutils.apis;

import org.jetbrains.annotations.NotNull;
import org.torusresearch.torusutils.apis.responses.VerifierLookupResponse.VerifierKey;

public class KeyResult {
    public final VerifierKey[] keys;
    public final Boolean is_new_key;

    public KeyResult(@NotNull VerifierKey[] keys, @NotNull Boolean is_new_key) {
        this.keys = keys;
        this.is_new_key = is_new_key;
    }
}

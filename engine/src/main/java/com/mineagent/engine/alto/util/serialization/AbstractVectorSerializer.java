// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/serialization/AbstractVectorSerializer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Collection;

public abstract class AbstractVectorSerializer<T> extends StdSerializer<T> {
    protected AbstractVectorSerializer() {
        this(null);
    }

    protected AbstractVectorSerializer(Class<T> vc) {
        super(vc);
    }

    protected abstract Collection<String> getParts(T value);

    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        Collection<String> parts = getParts(value);
        gen.writeString(String.join(",", parts));
    }
}

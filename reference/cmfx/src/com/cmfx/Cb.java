package com.cmfx;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Minimal Kotlin Function1 implementation usable from Java. */
public final class Cb implements Function1<Object, Unit> {

    public interface Sink {
        void ok(Object value);
        void fail(Throwable t);
    }

    private final Sink sink;
    private final String label;

    public Cb(String label, Sink sink) {
        this.label = label;
        this.sink = sink;
    }

    @Override
    public Unit invoke(Object arg) {
        Object v = Util.unboxResult(arg);
        if (v instanceof Throwable) {
            sink.fail((Throwable) v);
        } else {
            sink.ok(v);
        }
        return Unit.INSTANCE;
    }

    public String label() {
        return label;
    }
}

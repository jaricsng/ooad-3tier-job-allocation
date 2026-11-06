package edu.ooad.joballoc.business.proxy;

import java.lang.reflect.Method;

/**
 * One cross-cutting concern applied around a facade call (CHAIN OF RESPONSIBILITY). Each
 * interceptor does its job and then calls {@code next.proceed()}, or stops the chain by throwing.
 */
@FunctionalInterface
public interface Interceptor {

    Object intercept(Method method, Invocation next) throws Throwable;

    /** The rest of the chain, ending at the real service. */
    @FunctionalInterface
    interface Invocation {
        Object proceed() throws Throwable;
    }
}

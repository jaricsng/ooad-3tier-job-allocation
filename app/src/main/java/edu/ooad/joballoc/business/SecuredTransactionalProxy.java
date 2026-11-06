package edu.ooad.joballoc.business;

import edu.ooad.joballoc.business.port.TransactionRunner;
import edu.ooad.joballoc.business.proxy.ExceptionTranslationInterceptor;
import edu.ooad.joballoc.business.proxy.Interceptor;
import edu.ooad.joballoc.business.proxy.SecurityInterceptor;
import edu.ooad.joballoc.business.proxy.TransactionInterceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * PROXY (GoF structural): stands in for the real {@link AllocationService} and adds behaviour
 * around every call without the service or its callers knowing.
 *
 * <p>Refactored: the proxy no longer contains the concerns itself. It runs an ordered chain of
 * {@link Interceptor}s (CHAIN OF RESPONSIBILITY), each with a single responsibility:
 * <ol>
 *   <li>{@link SecurityInterceptor}: role check (protection proxy), before any database work</li>
 *   <li>{@link ExceptionTranslationInterceptor}: DAO exceptions -> business exceptions</li>
 *   <li>{@link TransactionInterceptor}: begin / commit / roll back</li>
 * </ol>
 * A new concern (timing, auditing of reads) is a new interceptor; this class does not change (Open/Closed).
 * This is how Spring's {@code @PreAuthorize} and {@code @Transactional} advice chain works.
 */
public final class SecuredTransactionalProxy implements InvocationHandler {

    private final Object target;
    private final List<Interceptor> chain;

    private SecuredTransactionalProxy(Object target, List<Interceptor> chain) {
        this.target = target;
        this.chain = List.copyOf(chain);
    }

    /** The standard chain: security, then exception translation, then the transaction. */
    public static <T> T wrap(Class<T> iface, T target, TransactionRunner tx) {
        return wrap(iface, target, List.of(new SecurityInterceptor(), new ExceptionTranslationInterceptor(),
                new TransactionInterceptor(tx)));
    }

    /** Wraps {@code target} with a custom interceptor chain (outermost first). */
    public static <T> T wrap(Class<T> iface, T target, List<Interceptor> chain) {
        Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface},
                new SecuredTransactionalProxy(target, chain));
        return iface.cast(proxy);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(target, args);
        }
        return proceed(0, method, args);
    }

    private Object proceed(int index, Method method, Object[] args) throws Throwable {
        if (index == chain.size()) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
        return chain.get(index).intercept(method, () -> proceed(index + 1, method, args));
    }
}

package edu.ooad.joballoc.business.proxy;

import edu.ooad.joballoc.business.port.TransactionRunner;

import java.lang.reflect.Method;

/** Runs methods marked {@link Transactional} as one unit of work through the {@link TransactionRunner} port. */
public final class TransactionInterceptor implements Interceptor {

    private final TransactionRunner tx;

    public TransactionInterceptor(TransactionRunner tx) {
        this.tx = tx;
    }

    @Override
    // The chain declares "throws Throwable": checked ones are wrapped, everything else is rethrown unchanged.
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Object intercept(Method method, Invocation next) throws Throwable {
        if (!method.isAnnotationPresent(Transactional.class)) {
            return next.proceed();
        }
        return tx.inTransaction(() -> {
            try {
                return next.proceed();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        });
    }
}

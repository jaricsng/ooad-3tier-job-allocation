package edu.ooad.joballoc.business.proxy;

import edu.ooad.joballoc.business.ConcurrencyConflictException;
import edu.ooad.joballoc.persistence.dao.DuplicateKeyException;
import edu.ooad.joballoc.persistence.dao.OptimisticLockException;

import java.lang.reflect.Method;

/**
 * Translates the DAO contract's exceptions into business exceptions the presentation tier
 * understands. It sits outside the transaction, so failures raised at commit time are translated too.
 * Before: the proxy inspected {@code SQLException.getSQLState() == "23505"} (vendor detail in the business tier).
 */
public final class ExceptionTranslationInterceptor implements Interceptor {

    @Override
    public Object intercept(Method method, Invocation next) throws Throwable {
        try {
            return next.proceed();
        } catch (OptimisticLockException e) {
            throw new ConcurrencyConflictException(e.getMessage() + ". Refresh and retry.", e);
        } catch (DuplicateKeyException e) {
            throw new ConcurrencyConflictException("That job was just allocated by someone else. Refresh and retry.",
                    e);
        }
    }
}

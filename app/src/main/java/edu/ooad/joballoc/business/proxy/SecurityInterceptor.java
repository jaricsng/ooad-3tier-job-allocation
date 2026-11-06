package edu.ooad.joballoc.business.proxy;

import edu.ooad.joballoc.business.security.Actor;
import edu.ooad.joballoc.business.security.ForbiddenException;
import edu.ooad.joballoc.business.security.RequiresRole;
import edu.ooad.joballoc.business.security.SecurityContext;

import java.lang.reflect.Method;

/** PROTECTION PROXY concern: enforces {@link RequiresRole} before anything else runs. */
public final class SecurityInterceptor implements Interceptor {

    @Override
    public Object intercept(Method method, Invocation next) throws Throwable {
        RequiresRole required = method.getAnnotation(RequiresRole.class);
        if (required != null) {
            Actor actor = SecurityContext.require();
            if (!actor.has(required.value())) {
                throw new ForbiddenException(actor.username() + " (" + actor.role() + ") may not call "
                        + method.getName() + "; requires " + required.value());
            }
        }
        return next.proceed();
    }
}

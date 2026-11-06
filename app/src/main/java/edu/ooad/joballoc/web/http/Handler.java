package edu.ooad.joballoc.web.http;

/** A request handler registered with the {@link Router}. */
@FunctionalInterface
public interface Handler {
    Response handle(Request request) throws Exception;
}

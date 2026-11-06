package edu.ooad.joballoc.web;

import edu.ooad.joballoc.web.http.Response;
import edu.ooad.joballoc.web.http.Router;
import edu.ooad.joballoc.web.view.Html;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Output encoding and routing. */
public class WebUnitTest {

    @Test
    public void escapesHtmlMetacharacters() {
        assertEquals("&lt;script&gt;alert(&#x27;x&#x27;)&lt;/script&gt; &amp; &quot;",
                Html.esc("<script>alert('x')</script> & \""));
        assertEquals("", Html.esc(null));
    }

    @Test
    public void buildsEncodedUrls() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("date", "2026-11-05");
        p.put("msg", "a&b c");
        p.put("skip", null);
        assertEquals("/board?date=2026-11-05&msg=a%26b+c", Html.url("/board", p));
    }

    @Test
    public void routerExtractsPathVariables() {
        Router r = new Router().add("GET", "/api/v1/jobs/{id}/eligible-workers", Router.Access.API,
                x -> new Response());
        var m = r.match("GET", "/api/v1/jobs/J-1105-01/eligible-workers").orElseThrow();
        assertEquals("J-1105-01", m.vars().get("id"));
        assertFalse(r.match("POST", "/api/v1/jobs/J-1/eligible-workers").isPresent());
        assertTrue("405 detection", r.hasPath("/api/v1/jobs/J-1/eligible-workers"));
        assertFalse(r.match("GET", "/api/v1/jobs/a/b/eligible-workers").isPresent());
    }
}

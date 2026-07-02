export interface Env {
  SUPABASE_URL: string;
}

const ALLOWED_PREFIXES = ["/auth/v1", "/rest/v1", "/storage/v1"];

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (!env.SUPABASE_URL) {
      return new Response("Missing SUPABASE_URL", { status: 500 });
    }

    const incomingUrl = new URL(request.url);
    if (!ALLOWED_PREFIXES.some((prefix) => incomingUrl.pathname.startsWith(prefix))) {
      return new Response("Not found", { status: 404 });
    }

    const supabaseUrl = new URL(env.SUPABASE_URL);
    const targetUrl = new URL(incomingUrl.pathname + incomingUrl.search, supabaseUrl);
    const headers = new Headers(request.headers);
    headers.set("host", supabaseUrl.host);

    // Auth requests and responses can carry one-time codes and session tokens.
    // Keep logs status-only so `wrangler tail` is useful without exposing secrets.
    const isAuth = incomingUrl.pathname.startsWith("/auth/v1");
    if (isAuth) {
      console.log(`[proxy] -> ${request.method} ${incomingUrl.pathname}`);
    }

    // GET/HEAD must not carry a body. Forwarding request.body (even an
    // empty stream) for these methods makes fetch throw
    // "Request with a GET or HEAD method cannot have a body" — which is
    // what broke GET /auth/v1/user after a successful PKCE exchange and
    // silently dropped the session, sending the user back to sign-in.
    const method = request.method.toUpperCase();
    const noBody = method === "GET" || method === "HEAD";

    const upstream = await fetch(targetUrl, {
      method: request.method,
      headers,
      body: noBody ? undefined : request.body,
      redirect: "manual",
    });

    if (isAuth) {
      console.log(`[proxy] <- ${request.method} ${incomingUrl.pathname} ${upstream.status}`);
    }

    return upstream;
  },
};

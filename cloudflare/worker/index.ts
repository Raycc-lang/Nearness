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

    return fetch(targetUrl, {
      method: request.method,
      headers,
      body: request.body,
      redirect: "manual",
    });
  },
};

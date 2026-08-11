// Node 24's native Request rejects jsdom's AbortSignal realm. React Router passes that
// signal during memory-router navigation, so the test-only wrapper omits it when the
// two platform implementations are incompatible. Browser production code is untouched.
const NativeRequest = globalThis.Request;

try {
  void new NativeRequest('http://localhost/test', {signal: new AbortController().signal});
} catch {
  globalThis.Request = class CompatibleRequest extends NativeRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) {
      super(input, init ? {...init, signal: undefined} : init);
    }
  } as typeof Request;
}

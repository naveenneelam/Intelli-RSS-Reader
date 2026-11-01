# Sample feeds

These fixtures exercise the parser, autodiscovery, extraction and OPML code paths. They are
also packaged in the APK at `assets/samples/` and used by the JVM unit tests.

| File | Purpose |
|---|---|
| `rss2.xml` | RSS 2.0 with `content:encoded`, CDATA, media thumbnail, podcast enclosure and video |
| `atom.xml` | Atom with XHTML content, relative links, icon and audio enclosure |
| `rdf.xml` | RSS 1.0 / RDF with Dublin Core metadata |
| `article.html` | Cluttered web page used to test readability extraction and HTML sanitization |
| `subscriptions.opml` | Nested folder OPML containing valid, relative and malformed entries |

To try them locally, serve this directory with any static HTTP server and enter the local
URL in **Add feed**. HTTPS is the default; for local HTTP, enable **Allow insecure HTTP
feeds** in Settings → Synchronization. The OPML preview can load `subscriptions.opml`
through the system document picker.

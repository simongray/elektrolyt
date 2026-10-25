# A public server with a login, October 2026

*Proposal of 25 October 2026 for Simon Gray, after the sceptic's pass in
[Unique features for Elektrolyt](unique-features-2026.md#what-the-sceptic-said)
([Claude Doc](https://claude.ai/artifact/Cn4kWF7SbamjyyNNqY6Dzu)).*


## Summary

The sceptic asked for one decision, a public server with a login, since it
would unblock four features: links into the app that open on a phone (4),
sharing into the installed app on a phone (15), the allow rule of offline
episodes (16) and a calendar of expected episodes (11). Sync through WebDAV
(14) and the gpodder API want a route on the app's own origin too.

Three of the four need the phone to reach the server, not a login.
Tailscale Serve gives that privately over HTTPS, the way podcast-clj's
server already runs. Only the calendar needs the public internet, since
Google Calendar adds a calendar by URL only when it's public. Google's
server sends no login, so the calendar's URL has to be the secret.

Whichever login you choose, the apps that aren't browsers need a password.
AntennaPod, the other gpodder apps and other WebDAV clients take a user
name and a password and nothing else. Passkeys, OIDC and IndieAuth would
each come with app passwords for them.

So I recommend:

1. **Now:** stay private behind Tailscale Serve with no login, and add the
   guards of podcast-clj's server: answer only to the tailnet name, and
   refuse what Tailscale Funnel brings. Then build `/media` with its allow
   rule, and `/dav`.
2. **When the server must be public:** podcast-clj's password, with Basic
   auth and a back-off after wrong guesses, behind Caddy. It's the one
   login that browsers and the other apps share, and it's already written
   and probed.
3. **For several listeners:** OIDC against Pocket ID, which signs people in
   with passkeys so that the app's server stores none, plus an app password
   per device for the other apps. IndieAuth through indieauth-clj is a
   second way in, first for you as simon.grays.blog.

What each option unblocks:

| Option | Links on a phone (4) | Share into (15) | Offline (16) | Calendar (11) | WebDAV (14) | gpodder |
| --- | --- | --- | --- | --- | --- | --- |
| 1. Tailscale Serve, no login | Your devices with Tailscale | Your phone with Tailscale | Yes | Download only | Yes | On the tailnet |
| 2. A password | Yes | Yes | Yes | With a secret URL | Yes | Yes |
| 3 to 5. Passkeys, OIDC, IndieAuth | Yes | Yes | Yes | With a secret URL | In the page, and in other apps with app passwords | With app passwords |

IndieAuth signs in only listeners with a website. A link handed to a friend
opens the app for them only if they're a listener on the server too, since
the proxy refuses anyone else. The link to the publisher's file, with its
`#t=`, is the one that travels.


## What the server has to answer for

### What each feature needs

- **Links into the app (4) and share into (15)** need the phone to load the
  app over HTTPS from the server. The share target works only in the
  installed app
  ([Web Share Target](https://developer.chrome.com/docs/capabilities/web-apis/web-share-target)),
  and installing it needs a secure origin. Localhost is one, but the phone
  can't reach it.
- **Offline episodes (16)** need `/media`, which streams an episode's file
  and passes the Range on, so that a download can resume. The report's
  condition is that it mustn't become an open media relay.
- **The calendar (11)** as a download needs nothing from the server. A
  subscription needs a URL that the calendar's own server fetches, and
  Google Calendar adds one only when it's public
  ([Google](https://support.google.com/calendar/answer/37100)). I couldn't
  confirm whether Apple's Calendar fetches a subscription from the phone or
  from iCloud. So a subscription needs a public server and a URL whose long
  random token is the secret, served over HTTPS, and replaced to revoke it
  ([W3C TAG on capability URLs](https://www.w3.org/TR/capability-urls/)).
  The server has no library to make the calendar from, so the page would
  write a copy of it there, made with ical-clj.
- **WebDAV (14)** is how a phone syncs, since its browser can't pick a
  folder. The page can't write to Nextcloud or Fastmail unless they allow
  it with CORS, so either the server serves `/dav` itself, as
  podcast-clj's server does with like-minded's handler, or the proxy
  forwards writes to a host that you name. The proxy answers GET and HEAD
  alone. Its
  [TODO](https://github.com/simongray/wary-fetch/blob/master/src/dk/simongray/wary_fetch/proxy.cljc#L127)
  sketches the forwarding to hosts that the operator names, never to hosts
  that users choose, which would make it a relay of writes for every
  account.
- **The gpodder API** has the same two shapes. Either the server serves
  `/api/2/` with podcast-clj's handler, so that AntennaPod syncs with it, or
  the proxy forwards Elektrolyt's own gpodder requests to gpodder.net or
  Nextcloud's GPodder Sync. Serving it makes the server one of the
  listener's devices, as podcast-clj's server is: the API names an episode
  by the URL of its file, which the server finds only in feeds that it
  fetched itself
  ([podcast-clj's server](https://github.com/simongray/podcast-clj/blob/master/server/README.md#antennapod-and-other-gpodder-apps)).

### What's on the server

Today the server has the Podcast Index's credentials and a folder of
thumbnails. The principle is that the browser keeps the listener's library,
so a public server should add only what sync needs and what its login
needs:

- **The sync documents,** if it serves `/dav`. The page can seal them with
  like-minded's
  [encrypted store](https://github.com/simongray/like-minded/blob/master/src/dk/simongray/like_minded/store/encrypted.cljc),
  so that the disk holds ciphertext alone. A server that serves the gpodder
  API can't read sealed documents, so the two don't go together.
- **The profile and a skeleton of each feed,** if it serves the gpodder
  API, as podcast-clj's server keeps them.
- **What its login needs,** which each option below lists.

### Security

- **The proxy** fetches any public URL with GET or HEAD for the pages of its
  own origin, 32 MB at most and four at a time
  ([server.clj](../src/dk/simongray/elektrolyt/server.clj#L250)). It
  refuses a navigation and a page of another site
  ([wary-fetch](https://github.com/simongray/wary-fetch/blob/master/src/dk/simongray/wary_fetch/proxy.cljc#L115)).
  Anyone who reaches it without a login has an open relay, whose requests
  publishers see from the server's address and may block. So every option
  puts it behind the login or the tailnet. The thumbnails at `/artwork` are
  a relay of images too, and go behind the same.
- **The media route,** `/media`, would stream up to 2 GB per answer for up
  to an hour
  ([wary-fetch](https://github.com/simongray/wary-fetch/blob/master/src/dk/simongray/wary_fetch/proxy.cljc#L280)).
  The podcast-clj server streams only the files of the episodes of the
  feeds that its own device fetched
  ([service.clj](https://github.com/simongray/podcast-clj/blob/master/server/podcast_server/service.clj#L484)),
  but Elektrolyt's server has no library to check against. Instead, the page
  can name the feed with the file, e.g. `/media?url=…&feed=…`, and the
  server streams the file only when that feed, fetched with its validators,
  lists it as an enclosure. That stores nothing. Neither rule stops someone
  with the login, who can write a feed that lists any file, but both keep
  `/media` from streaming a URL that no feed names.
- **DNS rebinding.** The server refuses any Host header but localhost
  ([server.clj](../src/dk/simongray/elektrolyt/server.clj#L37)). On a
  tailnet or a domain it has to answer to that name alone, as podcast-clj's
  `known-names` does
  ([service.clj](https://github.com/simongray/podcast-clj/blob/master/server/podcast_server/service.clj#L195)).
  Behind a login the check matters less, since a page that rebinds a name
  of its own has another origin and none of the credentials, but it costs
  nothing to keep.
- **Content Security Policy.** Scripts come from the server alone, with
  `'unsafe-eval'` for shadow-cljs's development builds
  ([server.clj](../src/dk/simongray/elektrolyt/server.clj#L279)). A public
  server should serve a release build without `'unsafe-eval'`, and add
  `frame-ancestors 'none'`. Images and media stay open to any host, since
  the artwork and the episodes come from the publishers.
- **Writes from other sites.** The server already refuses a write whose
  Sec-Fetch-Site names another site
  ([server.clj](../src/dk/simongray/elektrolyt/server.clj#L52)), which is
  also what stops another site's form from posting to `/dav` or the gpodder
  API.
- **Cookies and sessions,** for options 3 to 5. The browser gets a
  `__Host-Http-` cookie that's Secure, HttpOnly and SameSite=Strict, and
  any tokens stay on the server, as
  [RFC 10017](https://www.rfc-editor.org/rfc/rfc10017.html) asks of a
  backend for a browser app. Strict suits the app. Its files hold nothing,
  so they can load without the cookie, and a link opened from Signal shows
  the page, whose own requests then carry the cookie.
- **Rate limits.** In podcast-clj's server, an address waits a second after
  a wrong password, twice as long after each one more, up to a minute
  ([service.clj](https://github.com/simongray/podcast-clj/blob/master/server/podcast_server/service.clj#L301)).
  It asks for at least 16 characters, above the 15 that NIST asks of a
  password used alone
  ([SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html), section
  3.1.1.2). With several listeners, each needs limits of their own, so that
  one can't use up the others' turns: a quota on their sync folder, as
  podcast-clj's `/dav` has, and their own share of the proxy and `/media`.


## The options

### 1. Private, behind Tailscale Serve, with no login

Tailscale Serve brings the requests of your tailnet over HTTPS to the
server on localhost, at the machine's name, e.g.
https://pi.example.ts.net:8443. That's how podcast-clj's server runs with
its private-network setting. It refuses whatever Funnel brings from the
public internet, which Serve marks with a header that a client can't send
itself
([service.clj](https://github.com/simongray/podcast-clj/blob/master/server/podcast_server/service.clj#L213)).

- **Unblocks:** links into the app and share into, on your own devices
  while Tailscale is connected, once you install the app on your phone from
  its tailnet address. Offline episodes, with the allow rule. WebDAV
  through `/dav`, and the gpodder API for AntennaPod on the tailnet, with
  any user name and password. Not a calendar subscription in Google
  Calendar, which can't reach the tailnet.
- **On the server:** nothing for the login.
- **Security:** whoever is on the tailnet is the listener. The proxy and
  `/media` relay for the tailnet alone. The server must keep listening on
  localhost, since a request that bypasses Serve can carry any headers it
  likes ([Tailscale](https://tailscale.com/kb/1312/serve)).
- **Cost:** S in code. The names check, the Funnel refusal and the refusal
  to start beyond localhost without a password or the private setting are
  podcast-clj's. Then it takes one `tailscale serve` command and Tailscale
  on each of your devices. The installed app opens only while the phone's
  Tailscale is connected.
- **Several listeners:** Serve sets `Tailscale-User-Login` to the login
  name of whoever sent the request, for people on the tailnet and people
  you shared the machine with. It never sets it for a tagged device or
  through Funnel, and removes it from a client's request
  ([Tailscale](https://tailscale.com/kb/1312/serve)). The server could keep
  a sync folder per name with no login code at all. That suits a household,
  not strangers.

### 2. A password, with Basic auth and back-off

This is podcast-clj's server
([README](https://github.com/simongray/podcast-clj/blob/master/server/README.md#without-tailscale)).
Every request gives the password in Basic credentials, with any user name.
The server compares them by their SHA-256 in constant time, makes an
address that guessed wrong wait, hides the Authorization header from its
logs, and lets the gpodder API take the same password
([service.clj](https://github.com/simongray/podcast-clj/blob/master/server/podcast_server/service.clj#L337)).
Caddy with a domain, or Tailscale Funnel, puts HTTPS in front.

- **Unblocks:** everything that option 1 does, from anywhere, and the
  calendar with a secret URL beside the password.
- **On the server:** the password, in the environment, and in memory the
  addresses that guessed wrong in the last ten minutes.
- **Security:** whoever has the password is the listener, and podcast-clj's
  ceilings bound what a leaked one costs
  ([threat model](https://github.com/simongray/podcast-clj/blob/master/doc/background/threat-model-2026.md#someone-with-the-password)).
  Basic auth sends the password with every request, which is fine over
  HTTPS alone. There's no sign-out but closing the browser. Funnel ends TLS
  on your machine, but Tailscale limits its bandwidth, with no setting for
  it ([Tailscale](https://tailscale.com/kb/1223/funnel)), which matters for
  the files that `/media` streams. Caddy has no such limit, but needs ports
  80 and 443 and a DNS record that points at the machine
  ([Caddy](https://caddyserver.com/docs/automatic-https)).
- **Cost:** S in code, once the guards are in a library that both servers
  require. It needs a domain, Caddy and a password of 16 characters or
  more. What I couldn't find out is whether an app installed on an iPhone
  shows the browser's prompt for the password. There's no documentation,
  only a report of an installed
  site that opened blank in Orion
  ([Orion](https://orionfeedback.org/d/11309-install-site-as-app-app-doesnt-show-authentication-prompt)),
  so test it on your phone before you rely on it.
- **Several listeners:** a user name and a password each, with a store and
  a gpodder handler each, as the handler's docstring says
  ([server.cljc](https://github.com/simongray/podcast-clj/blob/master/src/dk/simongray/podcast/gpodder/server.cljc#L91)).
  There's no sign-up, since you add each one, so it suits a household at
  most.

### 3. Passkeys

The server is the relying party of
[WebAuthn Level 3](https://www.w3.org/TR/webauthn-3/), a W3C Recommendation
since 25 August 2026.

- **Unblocks:** the same as option 2 in the browser. The gpodder apps and
  other WebDAV clients can't use a passkey, so they need app passwords: one
  per device, shown once, stored as a hash and revoked one at a time, as
  [Nextcloud](https://docs.nextcloud.com/server/latest/user_manual/en/session_management.html)
  does it.
- **On the server:** for each listener, the id, public key, signature
  counter and backup flags of each passkey, and the challenge of each
  registration or sign-in until it's answered. Yubico's
  [java-webauthn-server](https://github.com/Yubico/java-webauthn-server)
  leaves storing all of it to the app. Then there are the sessions and the
  hashes of the app passwords.
- **Security:** no shared secret on the server, and nothing to phish. NIST
  allows synced passkeys up to AAL2
  ([SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html), sections
  2.3.2 and 3.1.6.1). A passkey is bound to the domain, so moving from the
  tailnet name to a domain of your own means registering again. Since ts.net
  is on the
  [Public Suffix List](https://publicsuffix.org/list/public_suffix_list.dat),
  each tailnet's name is a site of its own. Registration must be by
  invitation, e.g. a link that works once, or anyone who reaches the server
  signs up.
- **Cost:** L. A sign-in view and both ceremonies in the page, the Java
  library and a store for it, invitations, recovery for a lost phone,
  sessions and app passwords.
- **Several listeners:** any number, but the server then stores everyone's
  credentials and handles their recovery, which is the most that a thin
  server could take on.

### 4. OIDC

The server sends the browser to a provider, which signs the person in and
sends back who they are
([OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html)).
The providers that fit:

- **Google or GitHub,** with nothing to run, though they see each sign-in.
- **[Pocket ID](https://github.com/pocket-id/pocket-id),** which signs in
  with passkeys alone, is OpenID Connect Certified, and runs in one Docker
  container.
- **[tsidp](https://tailscale.com/docs/features/tsidp),** Tailscale's own,
  which signs in the people of the tailnet and is still experimental.

With any of them:

- **Unblocks:** the same as option 3, with app passwords for the other
  apps.
- **On the server:** the client's id and secret, the issuer and subject of
  each listener allowed in, the sessions and the app passwords. The
  provider stores the people and their passkeys.
- **Security:** RFC 10017's pattern for a backend: the tokens stay on the
  server, and the browser has a session cookie. The OAuth part (PKCE, the
  state and the exchange of the code) is what indieauth-clj does already.
  OIDC adds the ID token, whose signature the server checks against the
  provider's keys.
- **Cost:** M in code, for a sign-in view, the client, sessions and app
  passwords. Pocket ID is one more service to run and update. While it's
  down nobody can sign in, though sessions go on.
- **Several listeners:** the best of the five. You add people at the
  provider, and the server only maps each subject to that listener's store.

### 5. IndieAuth, later

The listener signs in as their website
([IndieAuth](https://indieauth.spec.indieweb.org/), Living Standard of 11
July 2024). People and apps are both URLs, and PKCE is required. The client
in [indieauth-clj](https://github.com/simongray/indieauth-clj) finds the
person's server, builds the request and redeems the code. It can carry the
session in a signed cookie, so the server stores none.

- **Unblocks:** the same as option 3, with app passwords, for listeners
  with a website.
- **On the server:** the profile URLs allowed in, the secret that signs the
  sessions, and the app passwords.
- **Security:** simon.grays.blog is an IndieAuth server already, which signs
  you in through GitHub
  ([indieblog](https://github.com/simongray/indieblog/blob/master/doc/plan-auth.md)),
  so Elektrolyt as its client stores no password of yours. The blog reads a
  client's name and redirect URLs from the client's own URL
  ([section 4.2](https://indieauth.spec.indieweb.org/#client-information-discovery)),
  which it can't reach at a tailnet name. So this wants the public domain,
  or a blog that accepts a client it can't read.
- **Cost:** S to M with indieauth-clj: a sign-in view with a field for the
  URL, and the callback.
- **Several listeners:** only people with a site that is or names an
  IndieAuth server. With
  [relmeauth-clj](https://github.com/simongray/relmeauth-clj), a GitHub or
  Mastodon account that the site links to will do, but someone without a
  site still can't sign in. That's why the report kept it as an option
  rather than the login.


## Recommendation

Go private first, with option 1. It gives your phone the installed app, the
share sheet, links into the app, offline episodes, `/dav` and AntennaPod's
sync, with code that podcast-clj's server already has, and the server
stores nothing new. A household on your tailnet can follow, each person by
their `Tailscale-User-Login`, still with no login code.

When the server must face the public internet, for the calendar in Google
Calendar or for someone outside the tailnet, turn on option 2 behind Caddy.
Every other option needs a password for the gpodder apps and WebDAV clients
anyway, and this is that password, written once for podcast-clj and probed
there. Put Caddy in front rather than Funnel, for the bandwidth that
`/media` needs.

For listeners beyond a household, add option 4 with Pocket ID for the
browser, keeping per-device app passwords for the other apps, and option 5
as a second button for people with a website. I wouldn't build passkeys
into the server itself. It's the largest of the five, and the server would
then store everyone's credentials and handle their recovery. Pocket ID
gives the same passkeys while the server stores a subject per listener.


## First steps

Each is small, and the first four wait on none of the questions below.

1. **Move the guards into a library.** The names check, the Funnel refusal,
   the same-origin writes, HSTS, the hidden Authorization header, the
   password with its back-off, and the refusal to start open are in
   podcast-clj's `server/`, which its jar doesn't ship. Move them where
   both servers can require them, and replace Elektrolyt's `localhost-only`
   and `same-origin-writes` with them. S.
2. **A name and a private network.** Elektrolyt's server takes its names
   and the private-network setting from the environment, still listens on
   localhost, and answers to its tailnet name. S.
3. **A release build to serve.** Serve a release build's folder, with a
   policy that drops `'unsafe-eval'` and adds `frame-ancestors 'none'`. S.
4. **Tailscale Serve, and the app on your phone.** Run `tailscale serve`,
   add the manifest that feature 15 needs, install the app from the tailnet
   address, and try the share sheet and a link with `?t=`. S, mostly not
   code.
5. **Media with the feed check.** Mount wary-fetch's stream handler at
   `/media`, so that it streams a file only when the feed named with it
   lists it. This is the server part of feature 16. S to M.
6. **WebDAV with a quota.** Mount like-minded's WebDAV handler over a
   folder at `/dav`, with podcast-clj's quota. This is the phone's way to
   sync for feature 14. S.

When the server goes public, the password and Caddy are S, and the
calendar's secret URL is S once you've answered its question.


## Questions for you

- Is Tailscale on each of your devices fine for now, with the app on your
  phone opening only while Tailscale is connected?
- Where will the server run, at home or on a VPS, and at the tailnet name
  or a domain of your own? Cookies and passkeys are bound to the name, so
  changing it later means signing in and registering passkeys again.
- Should the server serve the gpodder API, which makes it one of your
  devices with the profile in the clear? Or should Elektrolyt be a client
  of another gpodder server, through a forwarding route? The same goes for
  WebDAV: `/dav` on the server, or your own Nextcloud or Fastmail.
- Should the sync documents on the server be sealed with a passphrase?
  Then the server can't serve the gpodder API.
- Will you put a copy of the calendar of expected episodes at a secret
  public URL, or is the download enough?
- Are several listeners a household on your tailnet, or people you don't
  share a tailnet with? And would you run Pocket ID, or use Google, GitHub
  or your blog?
- Do the guards belong in podcast-clj, beside `util.ring`, or in a small
  library of their own, since nothing in them is about podcasts?
